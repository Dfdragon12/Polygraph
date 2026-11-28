package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.evaluados.dto.CitaCandidatoResponse;
import com.polygraph.erp.modules.evaluados.service.PortalCandidatoService;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.servicios.repository.SugerenciaDisponibilidadRepository;
import com.polygraph.erp.shared.enums.EstadoSugerencia;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.utils.AccesoGestor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lo que el programador necesita del candidato al agendar una poligrafía/visita: su dirección
 *  (para prellenar el lugar de la visita) y la sugerencia de horario que mandó desde su portal. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CitaCandidatoGestorService {

    public record CitaCandidatoGestorResponse(String direccionCandidato, CitaCandidatoResponse.Sugerencia sugerenciaPendiente) {}

    private final ServicioSubprocesoRepository subprocesoRepository;
    private final SugerenciaDisponibilidadRepository sugerenciaRepository;
    private final PortalCandidatoService portalCandidatoService;

    public CitaCandidatoGestorResponse obtener(Long idServicioSubproceso, Usuario actor) {
        ServicioSubproceso sp = subprocesoRepository.findById(idServicioSubproceso)
                .orElseThrow(() -> new ApiException("Asignación no encontrada", HttpStatus.NOT_FOUND));
        if (!PortalCandidatoService.esCita(sp)) {
            throw new ApiException("Este subproceso no es una cita con el candidato.", HttpStatus.BAD_REQUEST);
        }
        AccesoGestor.validar(sp, actor);
        var sugerencia = sugerenciaRepository
                .findFirstByServicioSubproceso_IdAndEstadoOrderByFechaCreacionDesc(sp.getId(), EstadoSugerencia.PENDIENTE)
                .map(PortalCandidatoService::aRespuesta)
                .orElse(null);
        return new CitaCandidatoGestorResponse(
                portalCandidatoService.direccionDeVisita(sp.getServicio()),
                sugerencia);
    }

    public List<Long> idsConSugerenciaPendiente(Usuario actor) {
        return sugerenciaRepository.idsSubprocesoConPendiente(actor.getRol() == Rol.ADMIN_POLYGRAPH, actor.getIdUsuario());
    }
}
