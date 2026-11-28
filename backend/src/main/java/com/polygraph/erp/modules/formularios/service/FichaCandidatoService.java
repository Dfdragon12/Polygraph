package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.DocumentoRequeridoProceso;
import com.polygraph.erp.modules.catalogo.repository.DocumentoRequeridoProcesoRepository;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.entity.DocumentoEvaluado;
import com.polygraph.erp.modules.evaluados.repository.DocumentoEvaluadoRepository;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService;
import com.polygraph.erp.modules.formularios.dto.ArchivoDescarga;
import com.polygraph.erp.modules.formularios.dto.FichaCandidatoResponse;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.shared.enums.EstadoRevisionDocumento;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ficha del candidato vista DESDE un subproceso: sus datos agrupados y los soportes (documentos)
 * que cargó con el link de hoja de vida, cruzados contra los que exige el proceso del servicio.
 * Mismo control de acceso que el formulario: analista/poligrafista/visitador solo sobre
 * subprocesos asignados a ellos; los demás roles internos en modo supervisión.
 */
@lombok.extern.slf4j.Slf4j
@Service
@RequiredArgsConstructor
public class FichaCandidatoService {

    private static final Set<Rol> ROLES_EJECUTORES = Set.of(Rol.ANALISTA_INTERNO, Rol.POLIGRAFISTA, Rol.VISITADOR);

    private final ServicioSubprocesoRepository subprocesoRepository;
    private final HojaVidaEvaluadoRepository hojaVidaRepository;
    private final DocumentoEvaluadoRepository documentoRepository;
    private final DocumentoRequeridoProcesoRepository documentoRequeridoRepository;
    private final AlmacenamientoDocumentosService almacenamientoService;
    private final CandidatoInfoService candidatoInfoService;
    private final com.polygraph.erp.modules.solicitudes.service.SolicitudExtraService solicitudExtraService;
    private final org.springframework.context.ApplicationEventPublisher eventos;

    @Transactional(readOnly = true)
    public FichaCandidatoResponse obtener(Long idServicioSubproceso, Usuario actor) {
        ServicioSubproceso subproceso = obtenerConAcceso(idServicioSubproceso, actor);
        Servicio servicio = subproceso.getServicio();
        Candidato candidato = servicio != null ? servicio.getCandidato() : null;

        List<DocumentoEvaluado> cargados = documentosDe(candidato);
        // Si subió el mismo tipo más de una vez, vale el más reciente (vienen ordenados desc).
        Map<String, DocumentoEvaluado> porTipo = cargados.stream()
                .collect(Collectors.toMap(d -> d.getTipoDocumento().toLowerCase(), Function.identity(), (a, b) -> a));

        List<FichaCandidatoResponse.Soporte> soportes = new ArrayList<>();
        Set<String> tiposRequeridos = new HashSet<>();
        List<DocumentoRequeridoProceso> requeridos = servicio != null && servicio.getProceso() != null
                ? documentoRequeridoRepository.findByProceso_IdProcesoOrderByOrdenAscNombreAsc(servicio.getProceso().getIdProceso())
                : List.of();
        for (DocumentoRequeridoProceso r : requeridos) {
            String clave = r.getClave().toLowerCase();
            tiposRequeridos.add(clave);
            DocumentoEvaluado d = porTipo.get(clave);
            soportes.add(soporte(r.getClave(), r.getNombre(), true, Boolean.TRUE.equals(r.getObligatorio()), d));
        }
        // Lo que subió por su cuenta y el proceso no pide — igual se muestra, no se pierde de vista.
        for (DocumentoEvaluado d : cargados) {
            if (tiposRequeridos.contains(d.getTipoDocumento().toLowerCase())) continue;
            soportes.add(soporte(d.getTipoDocumento(), nombreLegible(d.getTipoDocumento()), false, false, d));
        }

        return new FichaCandidatoResponse(candidatoInfoService.resolverFicha(candidato), soportes,
                puedeRevisar(subproceso, actor),
                candidatoInfoService.declaraciones(candidato).stream()
                        .map(d -> new FichaCandidatoResponse.Declaracion(d.pregunta(), d.respuesta(), d.detalle()))
                        .toList(),
                servicio != null ? solicitudExtraService.obtener(servicio.getIdServicio()) : null,
                servicio != null && solicitudExtraService.admitePreguntas(servicio.getProceso()));
    }

    private static FichaCandidatoResponse.Soporte soporte(String clave, String nombre, boolean requerido,
                                                          boolean obligatorio, DocumentoEvaluado d) {
        if (d == null) {
            return new FichaCandidatoResponse.Soporte(clave, nombre, requerido, obligatorio,
                    null, null, null, null, null, null, null);
        }
        Usuario revisor = d.getRevisadoPor();
        return new FichaCandidatoResponse.Soporte(clave, nombre, requerido, obligatorio,
                d.getId(), d.getNombreArchivo(), d.getFechaCarga(),
                d.getEstadoRevision() != null ? d.getEstadoRevision().name() : null,
                d.getObservacionRevision(),
                revisor != null ? (revisor.getNombre() + " " + java.util.Objects.toString(revisor.getApellido(), "")).trim() : null,
                d.getFechaRevision());
    }

    /** Revisar un documento del candidato con un resultado de la lista (Aprobado, Denegado,
     *  Ilegible, Vencido, Incompleto, No corresponde). Cualquier resultado que no sea Aprobado es una
     *  devolución: exige observación (es lo que lee el candidato) y deja el documento BLOQUEADO — ni
     *  aprobarlo ni cambiarle el motivo — hasta que el candidato cargue uno nuevo, que nace PENDIENTE.
     *  Uno aprobado sí se puede devolver después (corregir un error de quien revisó). */
    @Transactional
    public FichaCandidatoResponse revisarSoporte(Long idServicioSubproceso, Long idDocumento,
                                                 EstadoRevisionDocumento estado, String observacion, Usuario actor) {
        ServicioSubproceso subproceso = obtenerConAcceso(idServicioSubproceso, actor);
        if (!puedeRevisar(subproceso, actor)) {
            throw new ApiException("No tienes permiso para revisar los documentos de este candidato.", HttpStatus.FORBIDDEN);
        }
        if (estado == null || estado == EstadoRevisionDocumento.PENDIENTE) {
            throw new ApiException("Elige el resultado de la revisión.", HttpStatus.BAD_REQUEST);
        }
        String motivo = observacion != null && !observacion.isBlank() ? observacion.trim() : null;
        if (estado.esDevolucion() && motivo == null) {
            throw new ApiException("Escribe la observación: el candidato la verá para saber qué corregir.", HttpStatus.BAD_REQUEST);
        }
        Candidato candidato = subproceso.getServicio() != null ? subproceso.getServicio().getCandidato() : null;
        DocumentoEvaluado documento = documentosDe(candidato).stream()
                .filter(d -> d.getId().equals(idDocumento))
                .findFirst()
                .orElseThrow(() -> new ApiException("Soporte no encontrado", HttpStatus.NOT_FOUND));
        if (documento.getEstadoRevision() != null && documento.getEstadoRevision().esDevolucion()) {
            throw new ApiException("Este documento ya se devolvió (" + documento.getEstadoRevision().etiqueta()
                    + "). Se podrá revisar de nuevo cuando el candidato cargue el documento corregido.", HttpStatus.CONFLICT);
        }
        documento.setEstadoRevision(estado);
        documento.setObservacionRevision(motivo);
        documento.setRevisadoPor(actor);
        documento.setFechaRevision(java.time.LocalDateTime.now());
        documentoRepository.save(documento);
        if (estado.esDevolucion() && ROLES_EJECUTORES.contains(actor.getRol())) {
            eventos.publishEvent(new DocumentoDevueltoPorEjecutor(idServicioSubproceso,
                    nombreDocumento(subproceso, documento.getTipoDocumento()), estado, motivo, actor));
        }
        log.info("Soporte {} ({}) {} por {} desde el subproceso {}", idDocumento, documento.getTipoDocumento(),
                estado, actor.getEmail(), idServicioSubproceso);
        return obtener(idServicioSubproceso, actor);
    }

    /** Revisan: admin, el gestor del cliente (AccesoGestor ya lo validó en obtenerConAcceso), quien
     *  ejecuta ESTE subproceso (analista, poligrafista o visitador asignado) y el programador
     *  responsable de ESTE subproceso. */
    static boolean puedeRevisar(ServicioSubproceso subproceso, Usuario actor) {
        return switch (actor.getRol()) {
            case ADMIN_POLYGRAPH, GESTOR -> true;
            // Quien ejecuta ESTE subproceso (analista, poligrafista o visitador asignado): al
            // devolver, además queda una novedad automática (ver DocumentoDevueltoPorEjecutor).
            case ANALISTA_INTERNO, POLIGRAFISTA, VISITADOR -> subproceso.getUsuarioAsignado() != null
                    && actor.getIdUsuario().equals(subproceso.getUsuarioAsignado().getIdUsuario());
            case PROGRAMADOR -> subproceso.getProgramadorResponsable() != null
                    && actor.getIdUsuario().equals(subproceso.getProgramadorResponsable().getIdUsuario());
            default -> false;
        };
    }

    @Transactional(readOnly = true)
    public ArchivoDescarga descargarSoporte(Long idServicioSubproceso, Long idDocumento, Usuario actor) {
        ServicioSubproceso subproceso = obtenerConAcceso(idServicioSubproceso, actor);
        Candidato candidato = subproceso.getServicio() != null ? subproceso.getServicio().getCandidato() : null;
        DocumentoEvaluado documento = documentosDe(candidato).stream()
                .filter(d -> d.getId().equals(idDocumento))
                .findFirst()
                .orElseThrow(() -> new ApiException("Soporte no encontrado", HttpStatus.NOT_FOUND));
        if (documento.getRutaArchivo() == null) {
            throw new ApiException("Este soporte no tiene archivo asociado.", HttpStatus.NOT_FOUND);
        }
        return new ArchivoDescarga(almacenamientoService.cargarComoResource(documento.getRutaArchivo()),
                documento.getNombreArchivo(), almacenamientoService.tipoContenido(documento.getNombreArchivo()));
    }

    private List<DocumentoEvaluado> documentosDe(Candidato candidato) {
        if (candidato == null || candidato.getIdCandidato() == null) return List.of();
        return hojaVidaRepository.findByCandidato_IdCandidato(candidato.getIdCandidato())
                .map(hv -> documentoRepository.findByHojaVidaEvaluado_IdOrderByFechaCargaDesc(hv.getId()))
                .orElse(List.of());
    }

    private ServicioSubproceso obtenerConAcceso(Long id, Usuario actor) {
        ServicioSubproceso subproceso = subprocesoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Subproceso no encontrado", HttpStatus.NOT_FOUND));
        com.polygraph.erp.shared.utils.AccesoGestor.validar(subproceso, actor);
        if (ROLES_EJECUTORES.contains(actor.getRol())) {
            Usuario asignado = subproceso.getUsuarioAsignado();
            if (asignado == null || !actor.getIdUsuario().equals(asignado.getIdUsuario())) {
                throw new ApiException("Este subproceso no está asignado a ti.", HttpStatus.FORBIDDEN);
            }
        }
        return subproceso;
    }

    /** Nombre como lo configuró el proceso (ej. "Cédula por ambas caras"), o legible a partir de la clave. */
    private String nombreDocumento(ServicioSubproceso subproceso, String clave) {
        Servicio servicio = subproceso.getServicio();
        if (servicio != null && servicio.getProceso() != null) {
            for (DocumentoRequeridoProceso r : documentoRequeridoRepository
                    .findByProceso_IdProcesoOrderByOrdenAscNombreAsc(servicio.getProceso().getIdProceso())) {
                if (r.getClave() != null && r.getClave().equalsIgnoreCase(clave)) return r.getNombre();
            }
        }
        return nombreLegible(clave);
    }

    /** "acta_diploma" -> "Acta diploma" — para soportes que el proceso no tiene configurados. */
    private static String nombreLegible(String clave) {
        if (clave == null || clave.isBlank()) return "Documento";
        String texto = clave.replace('_', ' ').trim();
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
