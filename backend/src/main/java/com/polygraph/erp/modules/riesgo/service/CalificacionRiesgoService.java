package com.polygraph.erp.modules.riesgo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.ProcesoTipoProgreso;
import com.polygraph.erp.modules.catalogo.repository.ProcesoTipoProgresoRepository;
import com.polygraph.erp.modules.catalogo.service.CalificacionProcesoService;
import com.polygraph.erp.modules.formularios.entity.FormularioColumna;
import com.polygraph.erp.modules.formularios.entity.FormularioColumnaPuntaje;
import com.polygraph.erp.modules.formularios.entity.FormularioDato;
import com.polygraph.erp.modules.formularios.repository.FormularioDatoRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioSeccionRepository;
import com.polygraph.erp.modules.riesgo.dto.CalificacionRiesgoResponse;
import com.polygraph.erp.modules.riesgo.entity.CalificacionRiesgo;
import com.polygraph.erp.modules.riesgo.entity.ParametrosRiesgo;
import com.polygraph.erp.modules.riesgo.entity.UmbralRiesgo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.*;
import com.polygraph.erp.modules.riesgo.motor.CalificadorServicio;
import com.polygraph.erp.modules.riesgo.motor.MotorCalificacionRiesgo;
import com.polygraph.erp.modules.riesgo.repository.CalificacionRiesgoRepository;
import com.polygraph.erp.modules.riesgo.repository.ParametrosRiesgoRepository;
import com.polygraph.erp.modules.riesgo.repository.UmbralRiesgoRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.shared.enums.CriticidadCargo;
import com.polygraph.erp.shared.enums.EstadoAsignacion;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoDatoFormulario;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Conecta el motor de riesgo (Fase C) con los casos reales (Fase D, docs/modelo-calificacion-riesgo.md):
 * arma la entrada desde el catálogo (pesos, Fase A) y los formularios (respuestas con puntos,
 * Fase B), guarda cada cálculo que cambia el resultado (historial, nunca se sobrescribe), y
 * maneja la decisión del gestor y el bloqueo al publicar el informe.
 *
 * Mapeo de estados: COMPLETADO/PUBLICADO → REALIZADO; CANCELADO (incluye "No realizado") →
 * NO_REALIZADO; el resto → PENDIENTE (decisiones 25-sep-2026). Subprocesos del proceso que no
 * existen en el caso entran como NO_REALIZADO; subprocesos del caso que ya no están en el
 * proceso entran con peso 0 (no promedian, pero sus críticos cuentan).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalificacionRiesgoService {

    private final CalificacionRiesgoRepository calificacionRepository;
    private final ServicioRepository servicioRepository;
    private final ServicioSubprocesoRepository subprocesoRepository;
    private final ProcesoTipoProgresoRepository pasoRepository;
    private final FormularioRepository formularioRepository;
    private final FormularioDatoRepository datoRepository;
    private final FormularioSeccionRepository seccionRepository;
    private final UmbralRiesgoRepository umbralRepository;
    private final ParametrosRiesgoRepository parametrosRepository;
    private final UsuarioRepository usuarioRepository;
    private final CalificacionProcesoService calificacionProcesoService;
    private final ObjectMapper objectMapper;

    /** Valores de los marcadores del Word para un subproceso (docs/modelo-calificacion-riesgo.md §7). */
    public record MarcadoresWord(Map<String, String> riesgo, Map<String, String> calificacion) {}

    /** Riesgo que se muestra arriba del formulario de un subproceso. califica: la plantilla tiene
     *  campos que suman puntos. estado: EN_CURSO (puntaje parcial con lo guardado hasta ahora),
     *  REALIZADO o NO_REALIZADO. estudio: solo para gestor/admin (null para quien diligencia). */
    public record ResumenSubproceso(boolean califica, String estado, String nivel, BigDecimal puntaje,
                                    boolean tieneCritica, Estudio estudio) {}

    /** estado: CALCULADO / EN_PROCESO / SIN_INFORMACION / NO_CALIFICA; nivel = el que vale. */
    public record Estudio(String estado, String nivel, BigDecimal puntaje, boolean cambiadoPorGestor) {}

    /** Registro interno completo que se guarda en detalle_json. */
    public record DetalleCalculo(ConfiguracionCalculo configuracion, String criticidad, ResultadoCalificacion resultado) {}

    // ───────────────────────────── Recálculo ─────────────────────────────

    /** Se atiende después del commit de quien lo pidió, en su propia transacción: un error aquí
     *  se registra en el log y nunca revierte lo que el usuario guardó. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void alSolicitarRecalculo(RecalculoRiesgoSolicitado evento) {
        try {
            recalcular(evento.idServicio(), evento.idActor());
        } catch (Exception e) {
            log.error("No se pudo recalcular el riesgo del servicio {}: {}", evento.idServicio(), e.getMessage(), e);
        }
    }

    /** Recalcula y, solo si el resultado cambió, agrega una fila vigente nueva. Si el cálculo
     *  vigente está bloqueado y el servicio sigue PUBLICADO, no hace nada. Si el servicio ya NO está
     *  publicado (se devolvió a otro estado), se desbloquea y se recalcula (decisión 25-sep-2026). */
    @Transactional
    public CalificacionRiesgo recalcular(Integer idServicio, Long idActor) {
        Servicio servicio = servicio(idServicio);
        CalificacionRiesgo vigente = calificacionRepository.findFirstByServicio_IdServicioAndVigenteTrue(idServicio).orElse(null);
        if (vigente != null && Boolean.TRUE.equals(vigente.getBloqueado())) {
            if (servicio.getEstado() == EstadoServicio.PUBLICADO) return vigente;
            vigente.setBloqueado(false);
            log.info("Riesgo del servicio {} desbloqueado: el servicio salió de PUBLICADO (quedó {})",
                    idServicio, servicio.getEstado());
        }

        CriticidadCargo criticidad = criticidadDe(servicio);
        ConfiguracionCalculo config = configuracion(servicio, criticidad);
        ResultadoCalificacion resultado = MotorCalificacionRiesgo.calcular(config, entradas(servicio));
        String detalle = json(new DetalleCalculo(config, criticidad.name(), resultado));

        if (vigente != null && detalle.equals(vigente.getDetalleJson())) return vigente; // nada cambió

        if (vigente != null) {
            vigente.setVigente(false);
            calificacionRepository.saveAndFlush(vigente); // antes del insert: índice único de vigente
        }
        CalificacionRiesgo nuevo = calificacionRepository.save(CalificacionRiesgo.builder()
                .servicio(servicio)
                .vigente(true)
                .estado(resultado.estado())
                .puntajePonderado(resultado.puntajePonderado())
                .nivelPorPuntaje(resultado.nivelPorPuntaje())
                .puntajeFinal(resultado.puntajeFinal())
                .nivelFinal(resultado.nivelFinal())
                .tipoAjuste(resultado.tipoAjuste())
                .cobertura(resultado.cobertura())
                .requiereRevision(resultado.requiereRevisionManual())
                .criticidad(criticidad)
                .versionConfiguracion(config.version())
                .detalleJson(detalle)
                .calculadoPor(idActor != null ? usuarioRepository.findById(idActor).orElse(null) : null)
                .fechaCalculo(LocalDateTime.now())
                .build());
        log.info("Riesgo recalculado: servicio={} estado={} final={} nivel={} ajuste={}",
                idServicio, resultado.estado(), resultado.puntajeFinal(), resultado.nivelFinal(), resultado.tipoAjuste());
        return nuevo;
    }

    // ───────────────────────────── Consulta y acciones del gestor ─────────────────────────────

    @Transactional
    public CalificacionRiesgoResponse obtener(Integer idServicio, Usuario actor) {
        Servicio servicio = servicio(idServicio);
        validarLectura(servicio, actor);
        // Si todavía no hay ningún cálculo (casos anteriores a la Fase D), se hace el primero ya.
        if (calificacionRepository.findFirstByServicio_IdServicioAndVigenteTrue(idServicio).isEmpty()) {
            recalcular(idServicio, actor.getIdUsuario());
        }
        return toResponse(servicio, actor);
    }

    @Transactional
    public CalificacionRiesgoResponse cambiarCriticidad(Integer idServicio, String criticidad, Usuario actor) {
        Servicio servicio = servicio(idServicio);
        validarGestion(servicio, actor);
        validarNoBloqueado(idServicio);
        CriticidadCargo nueva = criticidad == null || criticidad.isBlank() ? null : criticidadValida(criticidad);
        servicio.setCriticidadCargo(nueva);
        servicio.setUsuarioCriticidad(actor);
        recalcular(idServicio, actor.getIdUsuario());
        log.info("Criticidad del servicio {} ajustada a {} por {}", idServicio, nueva, actor.getIdUsuario());
        return toResponse(servicio, actor);
    }

    /** El nivel final es AUTOMÁTICO (= la recomendación del motor) salvo que el gestor lo cambie,
     *  con justificación obligatoria. nivel vacío = volver al automático. La recomendación del motor
     *  nunca se borra: el cambio va aparte, en la misma fila vigente. */
    @Transactional
    public CalificacionRiesgoResponse decidir(Integer idServicio, String nivel, String justificacion, Usuario actor) {
        Servicio servicio = servicio(idServicio);
        validarGestion(servicio, actor);
        CalificacionRiesgo vigente = recalcular(idServicio, actor.getIdUsuario());
        if (Boolean.TRUE.equals(vigente.getBloqueado())) {
            throw new ApiException("El informe ya se publicó — la decisión quedó bloqueada.", HttpStatus.CONFLICT);
        }
        if (vigente.getEstado() != EstadoResultado.CALCULADO) {
            throw new ApiException("Todavía no hay una recomendación para decidir (" + etiquetaEstado(vigente.getEstado()) + ").",
                    HttpStatus.CONFLICT);
        }
        if (nivel == null || nivel.isBlank()) {
            // Volver al automático: vale otra vez la recomendación del motor.
            vigente.setNivelDecidido(null);
            vigente.setJustificacionDecision(null);
            vigente.setDecididoPor(actor);
            vigente.setFechaDecision(LocalDateTime.now());
            log.info("Riesgo del servicio {} vuelve al nivel automático ({}) por {}", idServicio, vigente.getNivelFinal(), actor.getIdUsuario());
            return toResponse(servicio, actor);
        }
        NivelRiesgo decidido;
        try {
            decidido = NivelRiesgo.valueOf(nivel);
        } catch (IllegalArgumentException e) {
            throw new ApiException("Nivel no válido.", HttpStatus.BAD_REQUEST);
        }
        if (decidido == vigente.getNivelFinal()) {
            // Elegir el mismo nivel que recomienda el motor = seguir en automático.
            vigente.setNivelDecidido(null);
            vigente.setJustificacionDecision(null);
            return toResponse(servicio, actor);
        }
        String texto = justificacion == null ? null : justificacion.trim();
        if (decidido != vigente.getNivelFinal() && (texto == null || texto.isEmpty())) {
            throw new ApiException("Explica por qué cambias el nivel recomendado (" + vigente.getNivelFinal() + " → " + decidido + ").",
                    HttpStatus.BAD_REQUEST);
        }
        vigente.setNivelDecidido(decidido);
        vigente.setJustificacionDecision(texto == null || texto.isEmpty() ? null : texto);
        vigente.setDecididoPor(actor);
        vigente.setFechaDecision(LocalDateTime.now());
        log.info("Decisión de riesgo: servicio={} recomendado={} decidido={} por {}",
                idServicio, vigente.getNivelFinal(), decidido, actor.getIdUsuario());
        return toResponse(servicio, actor);
    }

    /** Lo llama SolicitudService antes de pasar el servicio a PUBLICADO: si el proceso califica
     *  riesgo, exige que haya un resultado calculado (no "En proceso") y lo bloquea — desde aquí ya
     *  no cambia. La decisión del gestor NO es obligatoria: si no la cambió, vale la recomendación
     *  del motor (decisión 25-sep-2026, "automático con opción de cambiar"). */
    @Transactional
    public void prepararPublicacion(Servicio servicio, Usuario actor) {
        CalificacionRiesgo vigente = recalcular(servicio.getIdServicio(), actor.getIdUsuario());
        boolean procesoCalifica = servicio.getProceso() != null
                && calificacionProcesoService.resumen(servicio.getProceso().getIdProceso()).califica();
        if (procesoCalifica && vigente.getEstado() != EstadoResultado.CALCULADO) {
            throw new ApiException("No se puede publicar: el riesgo del evaluado está " + etiquetaEstado(vigente.getEstado())
                    + ". Revisa la pestaña Riesgo.", HttpStatus.CONFLICT);
        }
        vigente.setBloqueado(true);
        log.info("Riesgo del servicio {} bloqueado al publicar (nivel {} {})", servicio.getIdServicio(), nivelEfectivo(vigente),
                vigente.getNivelDecidido() != null ? "cambiado por el gestor" : "automático");
    }

    /** Nivel que vale: el que puso el gestor, o si no cambió nada, la recomendación del motor. */
    static NivelRiesgo nivelEfectivo(CalificacionRiesgo c) {
        return c.getNivelDecidido() != null ? c.getNivelDecidido() : c.getNivelFinal();
    }

    /** Marcadores del Word para el documento de un subproceso. riesgo: el nivel que vale y el
     *  puntaje final del estudio ("En proceso" si falta algo, "Sin calificar" si no aplica).
     *  calificacion: el del propio subproceso, vacío si todavía no se realizó. Nunca el registro
     *  interno (ponderado sin ajustar, tipo de ajuste, motivos). */
    @Transactional
    public MarcadoresWord marcadoresWord(Integer idServicio, Long idServicioSubproceso) {
        CalificacionRiesgo v = calificacionRepository.findFirstByServicio_IdServicioAndVigenteTrue(idServicio)
                .orElseGet(() -> recalcular(idServicio, null));
        Map<String, String> riesgo = new HashMap<>();
        switch (v.getEstado()) {
            case CALCULADO -> {
                riesgo.put("nivel", nivelEfectivo(v).name());
                riesgo.put("puntaje", formatoPuntaje(v.getPuntajeFinal()));
            }
            case EN_PROCESO -> { riesgo.put("nivel", "En proceso"); riesgo.put("puntaje", ""); }
            default -> { riesgo.put("nivel", "Sin calificar"); riesgo.put("puntaje", ""); }
        }
        Map<String, String> calificacion = new HashMap<>(Map.of("nivel", "", "puntaje", ""));
        leer(v.getDetalleJson()).resultado().servicios().stream()
                .filter(s -> String.valueOf(idServicioSubproceso).equals(s.codigo()) && s.puntajeFinal() != null)
                .findFirst()
                .ifPresent(s -> {
                    calificacion.put("nivel", s.nivel().name());
                    calificacion.put("puntaje", formatoPuntaje(s.puntajeFinal()));
                });
        return new MarcadoresWord(riesgo, calificacion);
    }

    /** Riesgo del subproceso para el encabezado del formulario (decisión 25-sep-2026: quien
     *  diligencia ve el nivel y puntaje de SU subproceso; el gestor, además, el del estudio).
     *  Mientras está en curso se califica con las respuestas guardadas (parcial), con la misma
     *  regla del motor (CalificadorServicio) y los umbrales de la criticidad del caso. */
    @Transactional
    public ResumenSubproceso resumenSubproceso(Long idServicioSubproceso, Usuario actor) {
        ServicioSubproceso sp = subprocesoRepository.findById(idServicioSubproceso)
                .orElseThrow(() -> new ApiException("Subproceso no encontrado", HttpStatus.NOT_FOUND));
        Servicio servicio = sp.getServicio();
        boolean veEstudio = actor.getRol() == Rol.ADMIN_POLYGRAPH || puedeGestionar(servicio, actor);
        boolean esSuyo = sp.getUsuarioAsignado() != null && Objects.equals(sp.getUsuarioAsignado().getIdUsuario(), actor.getIdUsuario());
        if (!veEstudio && !esSuyo) {
            throw new ApiException("No tienes acceso al riesgo de este subproceso.", HttpStatus.FORBIDDEN);
        }

        boolean califica = seccionRepository.findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(sp.getTipoProgreso().getIdTipoProgreso())
                .stream().filter(sec -> Boolean.TRUE.equals(sec.getActivo()))
                .flatMap(sec -> sec.getColumnas().stream())
                .anyMatch(c -> Boolean.TRUE.equals(c.getActivo()) && Boolean.TRUE.equals(c.getCalificaRiesgo()));

        String estado;
        String nivel = null;
        BigDecimal puntaje = null;
        boolean critica = false;
        if (sp.getEstado() == EstadoAsignacion.CANCELADO) {
            estado = "NO_REALIZADO";
        } else {
            estado = sp.getEstado() == EstadoAsignacion.COMPLETADO || sp.getEstado() == EstadoAsignacion.PUBLICADO
                    ? "REALIZADO" : "EN_CURSO";
            if (califica) {
                Umbrales umbrales = configuracion(servicio, criticidadDe(servicio)).umbrales();
                ResultadoServicio r = CalificadorServicio.calificar(new ServicioEvaluado(String.valueOf(sp.getId()),
                        sp.getTipoProgreso().getNombreProgreso(), BigDecimal.ZERO, false, EstadoSubproceso.REALIZADO, hallazgos(sp)), umbrales);
                nivel = r.nivel() != null ? r.nivel().name() : null;
                puntaje = r.puntajeFinal();
                critica = r.tieneCritico();
            }
        }

        Estudio estudio = null;
        if (veEstudio) {
            CalificacionRiesgo v = calificacionRepository.findFirstByServicio_IdServicioAndVigenteTrue(servicio.getIdServicio())
                    .orElseGet(() -> recalcular(servicio.getIdServicio(), actor.getIdUsuario()));
            NivelRiesgo efectivo = nivelEfectivo(v);
            estudio = new Estudio(v.getEstado().name(), efectivo != null ? efectivo.name() : null,
                    v.getPuntajeFinal(), v.getNivelDecidido() != null);
        }
        return new ResumenSubproceso(califica, estado, nivel, puntaje, critica, estudio);
    }

    /** 21.2 -> "21,2" (1 decimal, coma decimal). */
    static String formatoPuntaje(BigDecimal v) {
        return v == null ? "" : v.setScale(1, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    // ───────────────────────────── Entrada del motor ─────────────────────────────

    private CriticidadCargo criticidadDe(Servicio servicio) {
        if (servicio.getCriticidadCargo() != null) return servicio.getCriticidadCargo();
        Proceso proceso = servicio.getProceso();
        return proceso != null && proceso.getCriticidadDefecto() != null ? proceso.getCriticidadDefecto() : CriticidadCargo.ESTANDAR;
    }

    private ConfiguracionCalculo configuracion(Servicio servicio, CriticidadCargo criticidad) {
        UmbralRiesgo u = umbralRepository.findById(criticidad)
                .orElseThrow(() -> new ApiException("Faltan los umbrales de riesgo de " + criticidad, HttpStatus.INTERNAL_SERVER_ERROR));
        ParametrosRiesgo p = parametrosRepository.findById(ParametrosRiesgo.ID_UNICO)
                .orElseThrow(() -> new ApiException("Faltan los parámetros de riesgo", HttpStatus.INTERNAL_SERVER_ERROR));
        Proceso proceso = servicio.getProceso();
        return new ConfiguracionCalculo(
                new Umbrales(u.getLimiteBajo(), u.getLimiteMedio(), u.getMinimoMedio()),
                new ParametrosPiso(p.getUmbralServicioAlto(), p.getUmbralServicioModerado(), p.getCantidadServiciosModerados()),
                proceso != null ? proceso.getCoberturaMinima() : new BigDecimal("100"),
                "p" + (proceso != null ? proceso.getVersionCalificacion() : 0) + "-g" + p.getVersion());
    }

    List<ServicioEvaluado> entradas(Servicio servicio) {
        List<ProcesoTipoProgreso> pasos = servicio.getProceso() == null ? List.of()
                : pasoRepository.findByProceso_IdProcesoOrderByTipoProgreso_OrdenAscTipoProgreso_NombreProgresoAsc(servicio.getProceso().getIdProceso());
        Map<Integer, ProcesoTipoProgreso> pasoPorTipo = pasos.stream()
                .filter(p -> Boolean.TRUE.equals(p.getHabilitado()) && Boolean.TRUE.equals(p.getTipoProgreso().getActivo()))
                .collect(Collectors.toMap(p -> p.getTipoProgreso().getIdTipoProgreso(), Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<ServicioSubproceso> subprocesos = subprocesoRepository.findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(servicio.getIdServicio());

        List<ServicioEvaluado> entradas = new ArrayList<>();
        Set<Integer> tiposEnCaso = new HashSet<>();
        for (ServicioSubproceso sp : subprocesos) {
            Integer idTipo = sp.getTipoProgreso().getIdTipoProgreso();
            if (!tiposEnCaso.add(idTipo)) continue; // un tipo repetido en el caso cuenta una vez
            ProcesoTipoProgreso paso = pasoPorTipo.get(idTipo);
            EstadoSubproceso estado = switch (sp.getEstado()) {
                case COMPLETADO, PUBLICADO -> EstadoSubproceso.REALIZADO;
                case CANCELADO -> EstadoSubproceso.NO_REALIZADO;
                default -> EstadoSubproceso.PENDIENTE;
            };
            BigDecimal peso = paso != null ? paso.getPeso() : BigDecimal.ZERO; // ya no está en el proceso: peso 0
            entradas.add(new ServicioEvaluado(String.valueOf(sp.getId()), sp.getTipoProgreso().getNombreProgreso(), peso,
                    paso != null && Boolean.TRUE.equals(paso.getObligatorio()), estado,
                    estado == EstadoSubproceso.REALIZADO ? hallazgos(sp) : List.of()));
        }
        // Subprocesos del proceso que este caso no tiene: no se hicieron.
        pasoPorTipo.forEach((idTipo, paso) -> {
            if (tiposEnCaso.contains(idTipo)) return;
            entradas.add(new ServicioEvaluado("tipo-" + idTipo, paso.getTipoProgreso().getNombreProgreso() + " (no incluido en el caso)",
                    paso.getPeso(), Boolean.TRUE.equals(paso.getObligatorio()), EstadoSubproceso.NO_REALIZADO, List.of()));
        });
        return entradas;
    }

    /** Respuestas calificables elegidas en el formulario del subproceso → hallazgos del motor. */
    private List<Hallazgo> hallazgos(ServicioSubproceso sp) {
        return formularioRepository.findByServicioSubproceso_Id(sp.getId())
                .map(f -> datoRepository.findByFormulario_IdFormulario(f.getIdFormulario()).stream()
                        .map(this::hallazgoDe)
                        .filter(Objects::nonNull)
                        .toList())
                .orElse(List.of());
    }

    private Hallazgo hallazgoDe(FormularioDato d) {
        FormularioColumna c = d.getColumna();
        if (!Boolean.TRUE.equals(c.getCalificaRiesgo()) || !Boolean.TRUE.equals(c.getActivo())
                || d.getValor() == null || d.getValor().isBlank()) return null;
        String valor = d.getValor().trim();
        FormularioColumnaPuntaje p = c.getPuntajes().stream().filter(x -> x.getOpcion().equals(valor)).findFirst().orElse(null);
        if (p == null || (p.getPuntos().signum() == 0 && !Boolean.TRUE.equals(p.getEsCritico()))) return null;
        String respuesta = c.getTipoDato() == TipoDatoFormulario.BOOLEANO ? ("true".equals(valor) ? "Sí" : "No") : valor;
        String fila = d.getFila() != null && d.getFila().getEtiqueta() != null && !d.getFila().getEtiqueta().isBlank()
                ? " (" + d.getFila().getEtiqueta() + ")" : "";
        String campo = c.getNombre() != null ? c.getNombre() : c.getClave();
        return new Hallazgo(c.getSeccion().getNombre() + " › " + campo + fila + ": " + respuesta,
                p.getPuntos(), Boolean.TRUE.equals(p.getEsCritico()));
    }

    // ───────────────────────────── Respuesta ─────────────────────────────

    private CalificacionRiesgoResponse toResponse(Servicio servicio, Usuario actor) {
        Integer id = servicio.getIdServicio();
        List<CalificacionRiesgo> todas = calificacionRepository.findByServicio_IdServicioOrderByFechaCalculoDescIdDesc(id);
        CalificacionRiesgo v = todas.stream().filter(c -> Boolean.TRUE.equals(c.getVigente())).findFirst().orElse(null);
        ResultadoCalificacion r = v != null ? leer(v.getDetalleJson()).resultado() : null;
        Proceso proceso = servicio.getProceso();
        CalificacionProcesoService.ResumenPesos pesos = proceso != null ? calificacionProcesoService.resumen(proceso.getIdProceso()) : null;
        boolean gestiona = puedeGestionar(servicio, actor);
        boolean bloqueado = v != null && Boolean.TRUE.equals(v.getBloqueado());

        return new CalificacionRiesgoResponse(
                id, proceso != null ? proceso.getNombreProceso() : null,
                criticidadDe(servicio).name(),
                proceso != null && proceso.getCriticidadDefecto() != null ? proceso.getCriticidadDefecto().name() : null,
                servicio.getCriticidadCargo() != null,
                pesos != null && pesos.califica(), pesos != null ? pesos.motivo() : "El caso no tiene proceso",
                v != null ? v.getEstado().name() : null,
                v != null ? v.getPuntajePonderado() : null,
                v != null && v.getNivelPorPuntaje() != null ? v.getNivelPorPuntaje().name() : null,
                v != null ? v.getPuntajeFinal() : null,
                v != null && v.getNivelFinal() != null ? v.getNivelFinal().name() : null,
                v != null && v.getTipoAjuste() != null ? v.getTipoAjuste().name() : null,
                r != null ? r.motivosAjuste() : List.of(),
                v != null ? v.getCobertura() : null,
                proceso != null ? proceso.getCoberturaMinima() : null,
                v != null && Boolean.TRUE.equals(v.getRequiereRevision()),
                r != null ? r.motivosRevision() : List.of(),
                r != null ? r.servicios() : List.of(),
                v != null ? v.getVersionConfiguracion() : null,
                v != null ? v.getFechaCalculo() : null,
                v != null && v.getNivelDecidido() != null ? v.getNivelDecidido().name() : null,
                v != null && nivelEfectivo(v) != null ? nivelEfectivo(v).name() : null,
                v != null && v.getNivelDecidido() != null,
                v != null ? v.getJustificacionDecision() : null,
                v != null ? nombre(v.getDecididoPor()) : null,
                v != null ? v.getFechaDecision() : null,
                bloqueado,
                gestiona && !bloqueado && v != null && v.getEstado() == EstadoResultado.CALCULADO,
                gestiona && !bloqueado,
                todas.stream().map(c -> new CalificacionRiesgoResponse.Historial(c.getId(), c.getFechaCalculo(), c.getEstado().name(),
                        c.getPuntajeFinal(), c.getNivelFinal() != null ? c.getNivelFinal().name() : null,
                        c.getNivelDecidido() != null ? c.getNivelDecidido().name() : null, nombre(c.getCalculadoPor()))).toList());
    }

    // ───────────────────────────── Apoyo ─────────────────────────────

    /** Ver: admin (todo) y el gestor del cliente. */
    private void validarLectura(Servicio servicio, Usuario actor) {
        if (actor.getRol() == Rol.ADMIN_POLYGRAPH) return;
        if (!puedeGestionar(servicio, actor)) {
            throw new ApiException("No tienes acceso al riesgo de este caso.", HttpStatus.FORBIDDEN);
        }
    }

    /** Decidir y ajustar criticidad: solo el gestor del cliente (§6). */
    private void validarGestion(Servicio servicio, Usuario actor) {
        if (!puedeGestionar(servicio, actor)) {
            throw new ApiException("Solo el gestor del cliente puede decidir el riesgo de este caso.", HttpStatus.FORBIDDEN);
        }
    }

    private static boolean puedeGestionar(Servicio servicio, Usuario actor) {
        return actor.getRol() == Rol.GESTOR && servicio.getCliente() != null
                && Objects.equals(servicio.getCliente().getIdGestor(), actor.getIdUsuario());
    }

    private void validarNoBloqueado(Integer idServicio) {
        calificacionRepository.findFirstByServicio_IdServicioAndVigenteTrue(idServicio)
                .filter(c -> Boolean.TRUE.equals(c.getBloqueado()))
                .ifPresent(c -> { throw new ApiException("El informe ya se publicó — el riesgo quedó bloqueado.", HttpStatus.CONFLICT); });
    }

    private Servicio servicio(Integer id) {
        return servicioRepository.findById(id).orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
    }

    private static CriticidadCargo criticidadValida(String valor) {
        try {
            return CriticidadCargo.valueOf(valor);
        } catch (IllegalArgumentException e) {
            throw new ApiException("Criticidad no válida.", HttpStatus.BAD_REQUEST);
        }
    }

    private static String etiquetaEstado(EstadoResultado e) {
        return switch (e) {
            case EN_PROCESO -> "en proceso (hay subprocesos pendientes)";
            case SIN_INFORMACION -> "sin información (no se realizó ningún subproceso con peso)";
            case NO_CALIFICA -> "sin calificar (los pesos del proceso no están completos)";
            case CALCULADO -> "calculado";
        };
    }

    private static String nombre(Usuario u) {
        return u == null ? null : (u.getNombre() + (u.getApellido() != null ? " " + u.getApellido() : "")).trim();
    }

    private String json(DetalleCalculo d) {
        try {
            return objectMapper.writeValueAsString(d);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el detalle del cálculo de riesgo", e);
        }
    }

    private DetalleCalculo leer(String json) {
        try {
            return objectMapper.readValue(json, DetalleCalculo.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer el detalle del cálculo de riesgo", e);
        }
    }
}
