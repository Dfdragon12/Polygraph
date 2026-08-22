package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.TramoPrecioProceso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TramoPrecioProcesoRepository extends JpaRepository<TramoPrecioProceso, Integer> {
    List<TramoPrecioProceso> findByProceso_IdProcesoOrderByCantidadMinimaAsc(Integer idProceso);
    List<TramoPrecioProceso> findByProceso_IdProcesoOrderByCantidadMinimaDesc(Integer idProceso);
    boolean existsByProceso_IdProcesoAndCantidadMinima(Integer idProceso, Integer cantidadMinima);
    boolean existsByProceso_IdProcesoAndCantidadMinimaAndIdTramoNot(Integer idProceso, Integer cantidadMinima, Integer idTramo);
}
