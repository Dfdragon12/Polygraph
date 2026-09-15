package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.gestor.dto.HistorialLlamadaResponse;
import com.polygraph.erp.modules.servicios.entity.HistorialLlamadaConfirmacion;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.HistorialLlamadaConfirmacionRepository;
import com.polygraph.erp.shared.enums.EstadoConfirmacionAsistencia;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Único punto de escritura de la bitácora de llamadas de confirmación de asistencia — mismo
 * patrón que HistorialAsignacionService, para no perder el rastro de intentos anteriores cuando
 * ServicioSubproceso.estadoConfirmacion se sobrescribe con cada llamada nueva.
 */
@Service
@RequiredArgsConstructor
public class HistorialLlamadaService {

    private final HistorialLlamadaConfirmacionRepository repository;

    @Transactional
    public void registrar(ServicioSubproceso subproceso, EstadoConfirmacionAsistencia estado, String notas, Usuario actor) {
        repository.save(HistorialLlamadaConfirmacion.builder()
                .servicioSubproceso(subproceso)
                .estadoConfirmacion(estado)
                .notas(notas)
                .actor(actor)
                .build());
    }

    @Transactional(readOnly = true)
    public List<HistorialLlamadaResponse> listar(Long idServicioSubproceso) {
        return repository.findByServicioSubproceso_IdOrderByFechaLlamadaDesc(idServicioSubproceso).stream()
                .map(this::mapear)
                .toList();
    }

    private HistorialLlamadaResponse mapear(HistorialLlamadaConfirmacion h) {
        Usuario actor = h.getActor();
        String actorNombre = actor != null ? actor.getNombre() + (actor.getApellido() != null ? " " + actor.getApellido() : "") : null;
        return new HistorialLlamadaResponse(
                h.getId(),
                h.getEstadoConfirmacion().name(),
                h.getNotas(),
                actorNombre,
                h.getFechaLlamada());
    }
}
