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
            soportes.add(new FichaCandidatoResponse.Soporte(r.getClave(), r.getNombre(), true,
                    Boolean.TRUE.equals(r.getObligatorio()),
                    d != null ? d.getId() : null, d != null ? d.getNombreArchivo() : null, d != null ? d.getFechaCarga() : null));
        }
        // Lo que subió por su cuenta y el proceso no pide — igual se muestra, no se pierde de vista.
        for (DocumentoEvaluado d : cargados) {
            if (tiposRequeridos.contains(d.getTipoDocumento().toLowerCase())) continue;
            soportes.add(new FichaCandidatoResponse.Soporte(d.getTipoDocumento(), nombreLegible(d.getTipoDocumento()),
                    false, false, d.getId(), d.getNombreArchivo(), d.getFechaCarga()));
        }

        return new FichaCandidatoResponse(candidatoInfoService.resolverFicha(candidato), soportes);
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

    /** "acta_diploma" -> "Acta diploma" — para soportes que el proceso no tiene configurados. */
    private static String nombreLegible(String clave) {
        if (clave == null || clave.isBlank()) return "Documento";
        String texto = clave.replace('_', ' ').trim();
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
