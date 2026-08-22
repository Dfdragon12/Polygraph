package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.PrecioCiudadProceso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrecioCiudadProcesoRepository extends JpaRepository<PrecioCiudadProceso, Integer> {
    List<PrecioCiudadProceso> findByProceso_IdProcesoOrderByNivelCiudadAsc(Integer idProceso);

    void deleteByProceso_IdProceso(Integer idProceso);
}
