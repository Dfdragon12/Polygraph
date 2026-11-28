package com.polygraph.erp.modules.riesgo.repository;

import com.polygraph.erp.modules.riesgo.entity.UmbralRiesgo;
import com.polygraph.erp.shared.enums.CriticidadCargo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UmbralRiesgoRepository extends JpaRepository<UmbralRiesgo, CriticidadCargo> {}
