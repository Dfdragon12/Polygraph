package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.HistorialAsignacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialAsignacionRepository extends JpaRepository<HistorialAsignacion, Long> {
    List<HistorialAsignacion> findByServicioSubproceso_IdOrderByFechaEventoDesc(Long idServicioSubproceso);
}
