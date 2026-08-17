package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.HistorialEstadoServicio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialEstadoServicioRepository extends JpaRepository<HistorialEstadoServicio, Long> {
    List<HistorialEstadoServicio> findByServicio_IdServicioOrderByFechaCambioDesc(Integer idServicio);
}
