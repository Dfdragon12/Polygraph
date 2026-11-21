package com.polygraph.erp.modules.tienda.repository;

import com.polygraph.erp.modules.tienda.entity.PlanTienda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanTiendaRepository extends JpaRepository<PlanTienda, Integer> {
    List<PlanTienda> findByVisibleTrueOrderByUnidadesAsc();
}
