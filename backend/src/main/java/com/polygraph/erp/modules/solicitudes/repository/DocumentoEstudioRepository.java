package com.polygraph.erp.modules.solicitudes.repository;

import com.polygraph.erp.modules.solicitudes.entity.DocumentoEstudio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DocumentoEstudioRepository extends JpaRepository<DocumentoEstudio, Long> {

    Optional<DocumentoEstudio> findFirstByIdServicioAndVigenteTrueOrderByVersionDesc(Integer idServicio);

    Optional<DocumentoEstudio> findFirstByIdServicioOrderByVersionDesc(Integer idServicio);

    /** Los PDF vigentes de varios servicios de una vez (listas y Dashboard, sin consulta por fila). */
    List<DocumentoEstudio> findByIdServicioInAndVigenteTrue(java.util.Collection<Integer> ids);

    /** PC-01: los PDF que llevan demasiado en el estado dado (para dar por fallidos los GENERANDO que nunca terminaron). */
    List<DocumentoEstudio> findByEstadoAndFechaBefore(String estado, LocalDateTime limite);

    /** El estudio dejó de estar completo: ninguna versión se entrega al cliente hasta que se emita de nuevo. */
    @Modifying
    @Query("UPDATE DocumentoEstudio d SET d.vigente = false WHERE d.idServicio = :idServicio AND d.vigente = true")
    int invalidar(Integer idServicio);
}
