package com.polygraph.erp.modules.solicitudes.repository;

import com.polygraph.erp.modules.solicitudes.entity.SolicitudServicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface SolicitudServicioRepository extends JpaRepository<SolicitudServicio, Long> {

    @Query("SELECT COUNT(ss) > 0 FROM SolicitudServicio ss " +
           "WHERE ss.solicitud.cedulaEvaluado = :cedula " +
           "AND ss.catalogoServicio.idCatalogo = :idCatalogo " +
           "AND ss.solicitud.fechaSolicitud >= :fechaLimite " +
           "AND ss.solicitud.estado <> 'CANCELADO'")
    boolean existeDuplicado(@Param("cedula") String cedula,
                            @Param("idCatalogo") Integer idCatalogo,
                            @Param("fechaLimite") LocalDateTime fechaLimite);
}
