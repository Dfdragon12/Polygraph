package com.polygraph.erp.modules.riesgo.repository;

import com.polygraph.erp.modules.riesgo.entity.CentralRiesgo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CentralRiesgoRepository extends JpaRepository<CentralRiesgo, Integer> {

    List<CentralRiesgo> findAllByOrderByIdCentralAsc();

    /** La central que se usa para calificar: la primera activa. */
    Optional<CentralRiesgo> findFirstByActivoTrueOrderByIdCentralAsc();
}
