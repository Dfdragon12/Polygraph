package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.Pais;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaisRepository extends JpaRepository<Pais, Integer> {
    List<Pais> findAllByOrderByNombreAsc();
}
