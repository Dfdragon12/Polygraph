package com.polygraph.erp.modules.enlaces.repository;

import com.polygraph.erp.modules.enlaces.entity.EnlaceExterno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnlaceExternoRepository extends JpaRepository<EnlaceExterno, Integer> {

    List<EnlaceExterno> findByActivoTrueOrderByCategoriaAscNombreEntidadAsc();

    List<EnlaceExterno> findAllByOrderByCategoriaAscNombreEntidadAsc();
}
