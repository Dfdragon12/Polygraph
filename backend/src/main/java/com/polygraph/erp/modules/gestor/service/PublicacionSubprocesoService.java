package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.gestor.dto.AsignacionResponse;
import com.polygraph.erp.modules.formularios.service.ConclusionFormato;
import com.polygraph.erp.modules.formularios.service.ConclusionFormatoService;
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
    private final ConclusionFormatoService conclusionFormato;
    private final NovedadSubprocesoService novedadService;

    /**
     * PC-02: lo que la ventana Publicar necesita para abrirse con todo a la vista: la conclusión del ejecutor (la de su formato, no un cuadro vacío),
     * la calificación que sugiere el motor y si este subproceso la necesita para publicarse. Es la MISMA lectura que usa {@link #publicar}.
     */
    public record DatosPublicacion(String conclusion, String origenConclusion, boolean califica, boolean calculado,
                                   String nivel, BigDecimal puntaje, String motivoSinCalcular) {}

    @Transactional   // no es readOnly: el resumen del riesgo puede crear el cálculo vigente la primera vez
    public DatosPublicacion datosParaPublicar(Long id, Usuario actor) {
        ServicioSubproceso sp = asignacionService.obtenerOFallar(id);
        com.polygraph.erp.shared.utils.AccesoGestor.validar(sp, actor);
        var r = leerCalificacion(id, actor);
        return new DatosPublicacion(conclusionFormato.efectiva(sp), conclusionFormato.origen(sp),
                r.califica(), r.calculado(), r.calculado() ? r.nivel() : null, r.calculado() ? r.puntaje() : null, r.motivoSinCalcular());
    }

    /** La calificación del subproceso. Si no se puede leer NO se publica a ciegas: un fallo aquí ya no se traga. */
    private CalificacionRiesgoService.ResumenSubproceso leerCalificacion(Long id, Usuario actor) {
        try {
            return calificacionRiesgoService.resumenSubproceso(id, actor);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("No se pudo leer la calificación del subproceso {}: {}", id, e.getMessage());
            throw new ApiException("No se pudo leer la calificación de este subproceso. Intenta de nuevo en un momento.", HttpStatus.CONFLICT);
        }
    }

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
        // PC-09 (PB-01 C): primero de quién es el cliente, después el estado. Antes un gestor ajeno recibía «Solo se publica un subproceso en Finalizado» (409): ya sabía que existía y en qué estado estaba.
        com.polygraph.erp.shared.utils.AccesoGestor.validar(sp, actor);
        if (!req.revisado()) {
            throw new ApiException("Confirma que revisaste el informe y los soportes antes de publicar.", HttpStatus.BAD_REQUEST);
        }
        if (sp.getEstado() != EstadoSubproceso.FINALIZADO) {
            throw new ApiException("Solo se publica un subproceso en Finalizado (control de calidad).", HttpStatus.CONFLICT);
        }
        // Calificación: la sugerida por el motor; el gestor puede cambiarla, siempre con motivo.
        // PC-02 C: si el tipo de subproceso califica (tiene motor), no se publica sin nivel. Solo los que de verdad no califican salen sin él.
        var resumen = leerCalificacion(id, actor);
        if (resumen.califica() && (!resumen.calculado() || resumen.nivel() == null)) {
            String motivo = resumen.motivoSinCalcular();
            throw new ApiException("Este subproceso necesita su calificación para publicarse" + (motivo == null || motivo.isBlank() ? "." : ": " + motivo), HttpStatus.CONFLICT);
        }
        String sugerido = null;
        BigDecimal puntaje = null;
        if (resumen.calculado()) { sugerido = resumen.nivel(); puntaje = resumen.puntaje(); }
        String nivel = req.nivel() == null || req.nivel().isBlank() ? sugerido : req.nivel().trim().toUpperCase();
        if (nivel != null && !NIVELES.contains(nivel)) throw new ApiException("El nivel debe ser BAJO, MEDIO o ALTO.", HttpStatus.BAD_REQUEST);
        boolean cambioNivel = nivel != null && !nivel.equals(sugerido);
        if (cambioNivel && (req.motivoNivel() == null || req.motivoNivel().isBlank())) {
            throw new ApiException("Escribe el motivo del cambio de calificación.", HttpStatus.BAD_REQUEST);
        }
        // PC-02 A: el texto original es lo que escribió el ejecutor en su formato (o en el cuadro genérico); el gestor puede ajustarlo.
        String original = conclusionFormato.efectiva(sp);
        String finalTexto = req.conclusion() == null || req.conclusion().isBlank() ? original : req.conclusion().trim();
        if (finalTexto == null || finalTexto.isBlank()) {
            throw new ApiException("El cliente necesita una conclusión: escríbela antes de publicar.", HttpStatus.BAD_REQUEST);
        }
        // PC-02 B: el resultado lo da el nivel, no una palabra; con «favorable» o «desfavorable» no se publica.
        if (ConclusionFormato.contieneFavorable(finalTexto)) {
            throw new ApiException(ConclusionFormato.AVISO_FAVORABLE, HttpStatus.BAD_REQUEST);
        }
        if (sp.getConclusion() == null || sp.getConclusion().isBlank()) sp.setConclusion(original);   // informes enviados antes de PC-02: queda el original

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

    /** Los motivos de «Qué hay que corregir» y la clave del tipo de novedad que crea cada uno (V279). */
    static final Map<String, String> TIPOS_CORRECCION = Map.of(
            "DATO_ERRADO", "CORRECCION_DATO_ERRADO",
            "SOPORTE_EQUIVOCADO", "CORRECCION_SOPORTE_EQUIVOCADO",
            "HALLAZGO_MAL_CALIFICADO", "CORRECCION_HALLAZGO_MAL_CALIF",
            "OTRO", "CORRECCION_OTRO");
    private static final Map<String, String> NOMBRE_CORRECCION = Map.of(
            "DATO_ERRADO", "Dato errado", "SOPORTE_EQUIVOCADO", "Soporte equivocado", "HALLAZGO_MAL_CALIFICADO", "Hallazgo mal calificado", "OTRO", "Otro");

    /** A quién le llegó la solicitud (para el aviso «Solicitud enviada a …»); null si el cliente no tiene gestor. */
    public record CorreccionSolicitada(String gestor) {}

    /**
     * PC-04 · El ejecutor pide corregir un informe ya publicado. Queda (1) como NOVEDAD del subproceso para el gestor —del tipo que eligió—, (2) en el historial del informe y
     * (3) en un aviso al gestor del cliente. El cliente no ve nada de esto. El gestor decide: si está de acuerdo, el informe vuelve a edición.
     */
    @Transactional
    public CorreccionSolicitada solicitarCorreccion(Long id, String tipo, String motivo, Usuario actor) {
        ServicioSubproceso sp = asignacionService.obtenerOFallar(id);
        asignacionService.validarEsDelEjecutor(sp, actor);
        if (sp.getEstado() != EstadoSubproceso.PUBLICADO) {
            throw new ApiException("Solo se pide corrección de un informe publicado.", HttpStatus.CONFLICT);
        }
        String clave = tipo == null ? null : TIPOS_CORRECCION.get(tipo.trim().toUpperCase());
        if (clave == null) throw new ApiException("Elige qué hay que corregir: dato errado, soporte equivocado, hallazgo mal calificado u otro.", HttpStatus.BAD_REQUEST);
        if (motivo == null || motivo.isBlank()) throw new ApiException("Explícale al gestor qué hay que corregir.", HttpStatus.BAD_REQUEST);
        String queCorregir = NOMBRE_CORRECCION.get(tipo.trim().toUpperCase());
        String texto = motivo.trim();
        novedadService.registrarCorreccion(sp, clave, queCorregir + ": " + texto, actor);
        historia(id, "SOLICITUD_CORRECCION", null, queCorregir, texto, actor);
        var servicio = sp.getServicio();
        Long idGestor = servicio != null && servicio.getCliente() != null ? servicio.getCliente().getIdGestor() : null;
        if (idGestor == null) return new CorreccionSolicitada(null);
        String nombre = sp.getTipoProgreso().getNombreProgreso();
        var gestor = usuarioRepository.findById(idGestor).filter(u -> Boolean.TRUE.equals(u.getActivo())).orElse(null);
        if (gestor == null) return new CorreccionSolicitada(null);
        notificacionRepository.save(Notificacion.builder().usuario(gestor).tipo("CORRECCION_SOLICITADA")
                .titulo("Corrección solicitada · " + nombre)
                .mensaje(actor.getNombre() + " pide corregir \"" + nombre + "\", ya publicado (" + queCorregir.toLowerCase() + "): " + texto
                        + " Si estás de acuerdo, revierte el informe y devuélveselo para editar.")
                .leida(false).fechaCreacion(LocalDateTime.now()).realizadoPor(actor)
                .referenciaTipo("SERVICIO_SUBPROCESO").referenciaId(sp.getId()).build());
        return new CorreccionSolicitada((gestor.getNombre() == null ? "" : gestor.getNombre()) + (gestor.getApellido() == null ? "" : " " + gestor.getApellido()));
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
