package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.gestor.dto.AsignacionResponse;
import com.polygraph.erp.modules.gestor.dto.PublicarSubprocesoRequest;
import com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.entity.Notificacion;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PB-02: publicar un subproceso (FINALIZADO a PUBLICADO) con la conclusión que verá el cliente y la calificación del subproceso.
 * El estado lo cambia {@link AsignacionService#aprobar} (única máquina de estados); aquí van la conclusión, el nivel y el historial.
 * Todo cambio que hace una persona sobre un informe queda en historial_publicacion_subproceso: quién, cuándo, anterior, nuevo y motivo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicacionSubprocesoService {

    static final Set<String> NIVELES = Set.of("BAJO", "MEDIO", "ALTO");

    private final AsignacionService asignacionService;
    private final CalificacionRiesgoService calificacionRiesgoService;
    private final JdbcTemplate jdbc;
    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;

    /** El ejecutor escribe (o ajusta) su conclusión mientras el subproceso está en curso. */
    @Transactional
    public void guardarConclusion(Long id, String conclusion, Usuario actor) {
        ServicioSubproceso sp = asignacionService.obtenerOFallar(id);
        asignacionService.validarEsDelEjecutor(sp, actor);
        if (sp.getEstado() != EstadoSubproceso.EN_EJECUCION) {
            throw new ApiException("La conclusión solo se edita mientras el subproceso está en ejecución.", HttpStatus.CONFLICT);
        }
        sp.setConclusion(conclusion == null || conclusion.isBlank() ? null : conclusion.trim());
    }

    @Transactional
    public AsignacionResponse publicar(Long id, PublicarSubprocesoRequest req, Usuario actor, Set<Rol> roles) {
        ServicioSubproceso sp = asignacionService.obtenerOFallar(id);
        if (!req.revisado()) {
            throw new ApiException("Confirma que revisaste el informe y los soportes antes de publicar.", HttpStatus.BAD_REQUEST);
        }
        if (sp.getEstado() != EstadoSubproceso.FINALIZADO) {
            throw new ApiException("Solo se publica un subproceso en Finalizado (control de calidad).", HttpStatus.CONFLICT);
        }
        // Calificación: la sugerida por el motor; el gestor puede cambiarla, siempre con motivo.
        String sugerido = null;
        BigDecimal puntaje = null;
        try {
            var r = calificacionRiesgoService.resumenSubproceso(id, actor);
            if (r.calculado()) { sugerido = r.nivel(); puntaje = r.puntaje(); }
        } catch (RuntimeException e) {
            log.warn("No se pudo leer la calificación sugerida del subproceso {}: {}", id, e.getMessage());
        }
        String nivel = req.nivel() == null || req.nivel().isBlank() ? sugerido : req.nivel().trim().toUpperCase();
        if (nivel != null && !NIVELES.contains(nivel)) throw new ApiException("El nivel debe ser BAJO, MEDIO o ALTO.", HttpStatus.BAD_REQUEST);
        boolean cambioNivel = nivel != null && !nivel.equals(sugerido);
        if (cambioNivel && (req.motivoNivel() == null || req.motivoNivel().isBlank())) {
            throw new ApiException("Escribe el motivo del cambio de calificación.", HttpStatus.BAD_REQUEST);
        }
        String original = sp.getConclusion();
        String finalTexto = req.conclusion() == null || req.conclusion().isBlank() ? original : req.conclusion().trim();
        if (finalTexto == null || finalTexto.isBlank()) {
            throw new ApiException("El cliente necesita una conclusión: escríbela antes de publicar.", HttpStatus.BAD_REQUEST);
        }

        AsignacionResponse respuesta = asignacionService.aprobar(id, actor, roles);   // valida permisos y transición, y notifica

        sp.setConclusionPublicada(finalTexto);
        sp.setNivelPublicado(nivel);
        sp.setPuntajePublicado(puntaje);
        sp.setPublicadoPor(actor.getIdUsuario());
        if (!finalTexto.equals(original == null ? "" : original.trim())) {
            historia(id, "CAMBIO_CONCLUSION", original, finalTexto, null, actor);
        }
        if (cambioNivel) historia(id, "CAMBIO_NIVEL", sugerido, nivel, req.motivoNivel().trim(), actor);
        historia(id, "PUBLICAR", null, nivel, null, actor);
        return respuesta;
    }

    /** El ejecutor pide corregir un informe ya publicado: queda en el historial y le llega al gestor del cliente. */
    @Transactional
    public void solicitarCorreccion(Long id, String motivo, Usuario actor) {
        ServicioSubproceso sp = asignacionService.obtenerOFallar(id);
        asignacionService.validarEsDelEjecutor(sp, actor);
        if (sp.getEstado() != EstadoSubproceso.PUBLICADO) {
            throw new ApiException("Solo se pide corrección de un informe publicado.", HttpStatus.CONFLICT);
        }
        if (motivo == null || motivo.isBlank()) throw new ApiException("Cuenta qué hay que corregir.", HttpStatus.BAD_REQUEST);
        historia(id, "SOLICITUD_CORRECCION", null, null, motivo.trim(), actor);
        var servicio = sp.getServicio();
        Long idGestor = servicio != null && servicio.getCliente() != null ? servicio.getCliente().getIdGestor() : null;
        if (idGestor == null) return;
        String nombre = sp.getTipoProgreso().getNombreProgreso();
        usuarioRepository.findById(idGestor).filter(u -> Boolean.TRUE.equals(u.getActivo())).ifPresent(g ->
                notificacionRepository.save(Notificacion.builder().usuario(g).tipo("CORRECCION_SOLICITADA")
                        .titulo("Corrección solicitada · " + nombre)
                        .mensaje(actor.getNombre() + " pide corregir \"" + nombre + "\", ya publicado: " + motivo.trim())
                        .leida(false).fechaCreacion(LocalDateTime.now()).realizadoPor(actor)
                        .referenciaTipo("SERVICIO_SUBPROCESO").referenciaId(sp.getId()).build()));
    }

    /** Cambios hechos sobre el informe de un subproceso, del más reciente al más antiguo. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> historial(Long id) {
        return jdbc.queryForList("SELECT h.tipo, h.valor_anterior, h.valor_nuevo, h.motivo, h.fecha, "
                + "COALESCE(u.nombre,'') || ' ' || COALESCE(u.apellido,'') AS usuario FROM historial_publicacion_subproceso h "
                + "LEFT JOIN usuarios u ON u.id_usuario = h.id_usuario WHERE h.id_servicio_subproceso = ? ORDER BY h.fecha DESC, h.id DESC", id);
    }

    private void historia(Long id, String tipo, String anterior, String nuevo, String motivo, Usuario actor) {
        jdbc.update("INSERT INTO historial_publicacion_subproceso (id_servicio_subproceso, tipo, valor_anterior, valor_nuevo, motivo, id_usuario, fecha) VALUES (?,?,?,?,?,?,?)",
                id, tipo, anterior, nuevo, motivo, actor.getIdUsuario(), java.sql.Timestamp.valueOf(LocalDateTime.now()));
    }
}
