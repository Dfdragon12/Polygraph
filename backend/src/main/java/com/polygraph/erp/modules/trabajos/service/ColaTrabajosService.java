package com.polygraph.erp.modules.trabajos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.trabajos.config.PropiedadesTrabajos;
import com.polygraph.erp.modules.trabajos.dto.TrabajoAdminResponse;
import com.polygraph.erp.modules.trabajos.dto.TrabajoResponse;
import com.polygraph.erp.modules.trabajos.entity.TrabajoAsincrono;
import com.polygraph.erp.modules.trabajos.repository.TrabajoAsincronoRepository;
import com.polygraph.erp.shared.enums.EstadoTrabajo;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** Entrada y consulta de la cola: encolar, ver el estado propio, y las operaciones del panel
 *  (listar, reintentar). Ejecutar es cosa de DespachadorTrabajos. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ColaTrabajosService {

    private final TrabajoAsincronoRepository repository;
    private final PropiedadesTrabajos propiedades;
    private final AlmacenTrabajos almacen;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventos;

    /** Se publica al encolar: el despachador lo atiende después del commit, sin esperar al
     *  siguiente sondeo. */
    public record TrabajoEncolado(TipoTrabajo tipo) {}

    public TrabajoAsincrono encolar(TipoTrabajo tipo, Usuario usuario, Map<String, Object> parametros) {
        return encolar(tipo, usuario, parametros, null);
    }

    /** uuid propio cuando el llamador ya lo usó antes (ej. para nombrar el archivo de entrada). */
    public TrabajoAsincrono encolar(TipoTrabajo tipo, Usuario usuario, Map<String, Object> parametros, UUID uuid) {
        LocalDateTime ahora = LocalDateTime.now();
        TrabajoAsincrono trabajo = repository.save(TrabajoAsincrono.builder()
                .uuid(uuid != null ? uuid : UUID.randomUUID())
                .tipo(tipo)
                .estado(EstadoTrabajo.EN_COLA)
                .usuario(usuario)
                .parametrosJson(aJson(parametros))
                .progreso(0)
                .intentos(0)
                .maxIntentos(Math.max(1, propiedades.tipo(tipo).getMaxIntentos()))
                .disponibleDesde(ahora)
                .fechaCreacion(ahora)
                .build());
        eventos.publishEvent(new TrabajoEncolado(tipo));
        log.info("Trabajo encolado: {} {} por {}", tipo, trabajo.getUuid(), usuario.getEmail());
        return trabajo;
    }

    /** Para cuando el resultado ya existe (ej. PDF en caché): queda registrado como un trabajo
     *  más, así el frontend usa siempre el mismo flujo, pero no ocupa ningún hilo. */
    public TrabajoAsincrono registrarCompletado(TipoTrabajo tipo, Usuario usuario, Map<String, Object> parametros,
                                                ResultadoTrabajo resultado) {
        LocalDateTime ahora = LocalDateTime.now();
        TrabajoAsincrono trabajo = TrabajoAsincrono.builder()
                .uuid(UUID.randomUUID())
                .tipo(tipo)
                .estado(EstadoTrabajo.COMPLETADO)
                .usuario(usuario)
                .parametrosJson(aJson(parametros))
                .progreso(100)
                .intentos(0)
                .maxIntentos(Math.max(1, propiedades.tipo(tipo).getMaxIntentos()))
                .disponibleDesde(ahora)
                .fechaCreacion(ahora)
                .fechaInicio(ahora)
                .fechaFin(ahora)
                .build();
        aplicarResultado(trabajo, resultado);
        return repository.save(trabajo);
    }

    void aplicarResultado(TrabajoAsincrono trabajo, ResultadoTrabajo resultado) {
        if (resultado == null) return;
        trabajo.setResultadoJson(resultado.datos() != null ? aJson(resultado.datos()) : null);
        trabajo.setResultadoRuta(resultado.rutaArchivo());
        trabajo.setResultadoNombre(resultado.nombreArchivo());
        trabajo.setResultadoTipoContenido(resultado.tipoContenido());
    }

    // ───────────────────────── Consulta de quien lo pidió ─────────────────────────

    @Transactional(readOnly = true)
    public TrabajoResponse consultar(UUID uuid, Usuario usuario) {
        return aRespuesta(obtenerPropio(uuid, usuario));
    }

    /** El dueño, o el admin. Nadie más — ni siquiera sabiendo el uuid. */
    @Transactional(readOnly = true)
    public TrabajoAsincrono obtenerPropio(UUID uuid, Usuario usuario) {
        TrabajoAsincrono t = repository.findByUuid(uuid)
                .orElseThrow(() -> new ApiException("Trabajo no encontrado", HttpStatus.NOT_FOUND));
        boolean esDueno = t.getUsuario().getIdUsuario().equals(usuario.getIdUsuario());
        if (!esDueno && usuario.getRol() != Rol.ADMIN_POLYGRAPH) {
            // 404 y no 403: no confirmar que ese uuid existe.
            throw new ApiException("Trabajo no encontrado", HttpStatus.NOT_FOUND);
        }
        return t;
    }

    public TrabajoResponse aRespuesta(TrabajoAsincrono t) {
        Long posicion = t.getEstado() == EstadoTrabajo.EN_COLA
                ? repository.countByTipoAndEstadoAndIdLessThan(t.getTipo(), EstadoTrabajo.EN_COLA, t.getId()) + 1
                : null;
        JsonNode resultado = t.getEstado() == EstadoTrabajo.COMPLETADO ? leerArbol(t.getResultadoJson()) : null;
        boolean tieneArchivo = t.getEstado() == EstadoTrabajo.COMPLETADO && t.getResultadoRuta() != null;
        return new TrabajoResponse(t.getUuid(), t.getTipo().name(), t.getEstado().name(), t.getProgreso(),
                posicion, t.getMensajeUsuario(), resultado, tieneArchivo, t.getResultadoNombre(),
                t.getFechaCreacion(), t.getFechaFin());
    }

    // ───────────────────────────────── Panel admin ─────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TrabajoAdminResponse> listar(EstadoTrabajo estado, TipoTrabajo tipo, Pageable pageable) {
        return repository.buscar(estado, tipo, pageable).map(this::aRespuestaAdmin);
    }

    /** Saca un trabajo de la "dead letter queue" y lo vuelve a poner en la fila, con intentos en 0. */
    public TrabajoAdminResponse reintentar(UUID uuid, Usuario admin) {
        TrabajoAsincrono t = repository.findByUuid(uuid)
                .orElseThrow(() -> new ApiException("Trabajo no encontrado", HttpStatus.NOT_FOUND));
        if (t.getEstado() != EstadoTrabajo.FALLIDO) {
            throw new ApiException("Solo se pueden reintentar trabajos fallidos.", HttpStatus.CONFLICT);
        }
        String entrada = parametros(t).get("rutaEntrada") instanceof String r ? r : null;
        if (entrada != null && !almacen.existe(entrada)) {
            throw new ApiException("El archivo original de este trabajo ya no existe; hay que volver a subirlo.", HttpStatus.CONFLICT);
        }
        LocalDateTime ahora = LocalDateTime.now();
        t.setEstado(EstadoTrabajo.EN_COLA);
        t.setIntentos(0);
        t.setProgreso(0);
        t.setDisponibleDesde(ahora);
        t.setFechaInicio(null);
        t.setFechaFin(null);
        t.setMensajeUsuario(null);
        t.setErrorTecnico(null);
        eventos.publishEvent(new TrabajoEncolado(t.getTipo()));
        log.info("Trabajo {} {} reintentado a mano por {}", t.getTipo(), uuid, admin.getEmail());
        return aRespuestaAdmin(t);
    }

    private TrabajoAdminResponse aRespuestaAdmin(TrabajoAsincrono t) {
        Long duracion = t.getFechaInicio() != null && t.getFechaFin() != null
                ? Duration.between(t.getFechaInicio(), t.getFechaFin()).toMillis() : null;
        return new TrabajoAdminResponse(t.getUuid(), t.getTipo().name(), t.getEstado().name(),
                t.getUsuario() != null ? t.getUsuario().getEmail() : null,
                t.getProgreso(), t.getIntentos(), t.getMaxIntentos(), t.getMensajeUsuario(), t.getErrorTecnico(),
                t.getFechaCreacion(), t.getFechaInicio(), t.getFechaFin(), t.getDisponibleDesde(), duracion,
                t.getEstado() == EstadoTrabajo.FALLIDO);
    }

    // ───────────────────────────────── JSON ─────────────────────────────────

    public Map<String, Object> parametros(TrabajoAsincrono t) {
        try {
            return objectMapper.readValue(t.getParametrosJson(), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Parámetros inválidos en el trabajo " + t.getUuid(), e);
        }
    }

    private String aJson(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar a JSON", e);
        }
    }

    private JsonNode leerArbol(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            log.warn("resultado_json ilegible: {}", e.getMessage());
            return null;
        }
    }
}
