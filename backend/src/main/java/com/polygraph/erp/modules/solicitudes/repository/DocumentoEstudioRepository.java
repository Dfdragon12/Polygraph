package com.polygraph.erp.modules.solicitudes.repository;

import com.polygraph.erp.modules.solicitudes.entity.DocumentoEstudio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface DocumentoEstudioRepository extends JpaRepository<DocumentoEstudio, Long> {

    Optional<DocumentoEstudio> findFirstByIdServicioAndVigenteTrueOrderByVersionDesc(Integer idServicio);

    Optional<DocumentoEstudio> findFirstByIdServicioOrderByVersionDesc(Integer idServicio);

    /** El estudio dejó de estar completo: ninguna versión se entrega al cliente hasta que se emita de nuevo. */
    @Modifying
    @Query("UPDATE DocumentoEstudio d SET d.vigente = false WHERE d.idServicio = :idServicio AND d.vigente = true")
    int invalidar(Integer idServicio);
}
