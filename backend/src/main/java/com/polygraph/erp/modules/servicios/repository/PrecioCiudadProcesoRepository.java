package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.PrecioCiudadProceso;
import com.polygraph.erp.shared.enums.NivelCiudad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrecioCiudadProcesoRepository extends JpaRepository<PrecioCiudadProceso, Integer> {
    List<PrecioCiudadProceso> findByProceso_IdProcesoOrderByNivelCiudadAsc(Integer idProceso);

    Optional<PrecioCiudadProceso> findByProceso_IdProcesoAndNivelCiudad(Integer idProceso, NivelCiudad nivelCiudad);

    void deleteByProceso_IdProceso(Integer idProceso);
}
