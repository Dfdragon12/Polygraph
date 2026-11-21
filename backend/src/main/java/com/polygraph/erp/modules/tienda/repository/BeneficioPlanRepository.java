package com.polygraph.erp.modules.tienda.repository;

import com.polygraph.erp.modules.tienda.entity.BeneficioPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BeneficioPlanRepository extends JpaRepository<BeneficioPlan, Integer> {
    List<BeneficioPlan> findByProceso_IdProcesoOrderByDesdeUnidadesAscOrdenAsc(Integer idProceso);

    List<BeneficioPlan> findByProceso_IdProcesoOrderByOrdenAsc(Integer idProceso);

    void deleteByProceso_IdProceso(Integer idProceso);
}
