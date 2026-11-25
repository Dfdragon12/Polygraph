package com.polygraph.erp.modules.tienda.repository;

import com.polygraph.erp.modules.tienda.entity.HistorialPrecioPospago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialPrecioPospagoRepository extends JpaRepository<HistorialPrecioPospago, Long> {

    /** Las últimas 50 filas de un servicio, la más reciente primero. */
    List<HistorialPrecioPospago> findTop50ByIdProcesoOrderByFechaDescIdDesc(Integer idProceso);
}
