package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.ProcesoTipoProgreso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ProcesoTipoProgresoRepository extends JpaRepository<ProcesoTipoProgreso, Integer> {
    // El orden siempre lo define el catálogo (tipos_progreso.orden) — es la cadena canónica
    // de subprocesos, independiente de cualquier valor manual por proceso.
    List<ProcesoTipoProgreso> findByProceso_IdProcesoOrderByTipoProgreso_OrdenAscTipoProgreso_NombreProgresoAsc(Integer idProceso);
    long countByProceso_IdProceso(Integer idProceso);
    boolean existsByProceso_IdProcesoAndTipoProgreso_IdTipoProgreso(Integer idProceso, Integer idTipoProgreso);
    List<ProcesoTipoProgreso> findByTipoProgreso_IdTipoProgreso(Integer idTipoProgreso);

    @Query("SELECT SUM(ptp.tipoProgreso.valor) FROM ProcesoTipoProgreso ptp " +
           "WHERE ptp.proceso.idProceso = :idProceso AND ptp.habilitado = true")
    BigDecimal sumValorHabilitadosByProcesoId(@Param("idProceso") Integer idProceso);
}
