package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.gestor.dto.HistorialAsignacionResponse;
import com.polygraph.erp.modules.servicios.entity.HistorialAsignacion;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.HistorialAsignacionRepository;
import com.polygraph.erp.shared.enums.AccionHistorialAsignacion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Único punto de escritura de la bitácora de asignaciones (asignar, reasignar, desasignar,
 * completar, cancelar) — así ningún cambio de estado de un servicio_subproceso queda sin rastro,
 * ni la forma de registrar el evento diverge entre los distintos puntos que lo disparan.
 */
@Service
@RequiredArgsConstructor
public class HistorialAsignacionService {

    private final HistorialAsignacionRepository repository;

    @Transactional
    public void registrar(ServicioSubproceso subproceso, AccionHistorialAsignacion accion,
                           Usuario usuarioAnterior, Usuario usuarioNuevo,
                           LocalDateTime fechaProgramadaAnterior, LocalDateTime fechaProgramadaNueva,
                           Usuario actor, String observaciones) {
        repository.save(HistorialAsignacion.builder()
                .servicioSubproceso(subproceso)
                .accion(accion)
                .usuarioAnterior(usuarioAnterior)
                .usuarioNuevo(usuarioNuevo)
                .fechaProgramadaAnterior(fechaProgramadaAnterior)
                .fechaProgramadaNueva(fechaProgramadaNueva)
                .actor(actor)
                .observaciones(observaciones)
                .fechaEvento(LocalDateTime.now())
                .build());
    }

    @Transactional(readOnly = true)
    public List<HistorialAsignacionResponse> listar(Long idServicioSubproceso) {
        return repository.findByServicioSubproceso_IdOrderByFechaEventoDesc(idServicioSubproceso).stream()
                .map(this::mapear)
                .toList();
    }

    private HistorialAsignacionResponse mapear(HistorialAsignacion h) {
        return new HistorialAsignacionResponse(
                h.getId(),
                h.getAccion().name(),
                nombreCompleto(h.getUsuarioAnterior()),
                nombreCompleto(h.getUsuarioNuevo()),
                h.getFechaProgramadaAnterior(),
                h.getFechaProgramadaNueva(),
                nombreCompleto(h.getActor()),
                h.getObservaciones(),
                h.getFechaEvento());
    }

    private String nombreCompleto(Usuario u) {
        if (u == null) return null;
        return u.getNombre() + (u.getApellido() != null ? " " + u.getApellido() : "");
    }
}
