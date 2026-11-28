package com.polygraph.erp.modules.tienda.repository;

import com.polygraph.erp.modules.tienda.entity.HistorialPrecioProceso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialPrecioProcesoRepository extends JpaRepository<HistorialPrecioProceso, Long> {
    List<HistorialPrecioProceso> findTop200ByIdProcesoOrderByFechaDescIdHistorialDesc(Integer idProceso);
}
