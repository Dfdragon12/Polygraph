package com.polygraph.erp.modules.solicitudes.repository;

import com.polygraph.erp.modules.solicitudes.entity.HistorialSolicitud;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialSolicitudRepository extends JpaRepository<HistorialSolicitud, Long> {

    List<HistorialSolicitud> findBySolicitud_IdSolicitudOrderByFechaCambioDesc(Long idSolicitud);
}
