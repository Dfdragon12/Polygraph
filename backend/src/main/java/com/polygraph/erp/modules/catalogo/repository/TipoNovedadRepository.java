package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.TipoNovedad;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TipoNovedadRepository extends JpaRepository<TipoNovedad, Integer> {

    @EntityGraph(attributePaths = "categoria")
    List<TipoNovedad> findAllByOrderByCategoria_OrdenAscOrdenAscNombreAsc();

    boolean existsByClave(String clave);

    @EntityGraph(attributePaths = {"categoria", "roles"})
    java.util.Optional<TipoNovedad> findByClave(String clave);

    long countByCategoria_IdCategoriaNovedad(Integer idCategoria);
}
