package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.SolicitudReprogramacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SolicitudReprogramacionRepository extends JpaRepository<SolicitudReprogramacion, Long> {
    List<SolicitudReprogramacion> findByServicio_IdServicioOrderByFechaDesc(Integer idServicio);
}
