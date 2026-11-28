package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.HistorialLlamadaConfirmacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialLlamadaConfirmacionRepository extends JpaRepository<HistorialLlamadaConfirmacion, Long> {
    List<HistorialLlamadaConfirmacion> findByServicioSubproceso_IdOrderByFechaLlamadaDesc(Long idServicioSubproceso);
}
