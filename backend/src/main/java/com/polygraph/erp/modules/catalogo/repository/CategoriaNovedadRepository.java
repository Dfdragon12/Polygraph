package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.CategoriaNovedad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaNovedadRepository extends JpaRepository<CategoriaNovedad, Integer> {
    List<CategoriaNovedad> findAllByOrderByOrdenAscNombreAsc();

    boolean existsByClave(String clave);
}
