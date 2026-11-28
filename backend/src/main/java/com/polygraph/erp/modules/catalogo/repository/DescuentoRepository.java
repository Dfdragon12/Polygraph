package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.Descuento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DescuentoRepository extends JpaRepository<Descuento, Long> {

    Optional<Descuento> findByCodigoIgnoreCase(String codigo);

    List<Descuento> findAllByOrderByFechaCreacionDesc();

    @Query("""
            SELECT d FROM Descuento d
            WHERE d.activo = true
              AND (d.fechaInicio IS NULL OR d.fechaInicio <= :ahora)
              AND (d.fechaFin IS NULL OR d.fechaFin >= :ahora)
              AND (
                d.alcance = com.polygraph.erp.shared.enums.AlcanceDescuento.GLOBAL
                OR (d.alcance = com.polygraph.erp.shared.enums.AlcanceDescuento.PROCESO AND d.proceso.idProceso = :idProceso)
                OR (d.alcance = com.polygraph.erp.shared.enums.AlcanceDescuento.CATEGORIA AND d.clasificacion.idClasificacion = :idClasificacion)
              )
            """)
    List<Descuento> findVigentesParaProceso(@Param("idProceso") Integer idProceso,
                                             @Param("idClasificacion") Integer idClasificacion,
                                             @Param("ahora") LocalDateTime ahora);
}
