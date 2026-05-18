package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.CatalogoServicio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogoServicioRepository extends JpaRepository<CatalogoServicio, Integer> {
    List<CatalogoServicio> findByActivoTrueOrderByCategoriaAscOrdenAsc();
}
