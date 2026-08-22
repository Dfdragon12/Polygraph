package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.EstadoAsignacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface ServicioSubprocesoRepository
        extends JpaRepository<ServicioSubproceso, Long>, JpaSpecificationExecutor<ServicioSubproceso> {

    List<ServicioSubproceso> findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(Integer idServicio);

    List<ServicioSubproceso> findByServicio_IdServicioInOrderByServicio_IdServicioAscTipoProgreso_OrdenAsc(
            List<Integer> idsServicio);

    List<ServicioSubproceso> findByFechaProgramadaBetween(LocalDateTime desde, LocalDateTime hasta);

    boolean existsByServicio_IdServicioAndTipoProgreso_IdTipoProgreso(Integer idServicio, Integer idTipoProgreso);

    /** Carga actual de un empleado — usada para repartir la autoasignación al que tenga menos pendientes. */
    long countByUsuarioAsignado_IdUsuarioAndEstado(Long idUsuario, EstadoAsignacion estado);
}
