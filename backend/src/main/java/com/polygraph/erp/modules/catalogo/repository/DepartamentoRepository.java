package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.Departamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartamentoRepository extends JpaRepository<Departamento, Integer> {
    List<Departamento> findAllByOrderByNombreAsc();

    Optional<Departamento> findByNombreIgnoreCase(String nombre);

    boolean existsByCodigoDaneIgnoreCase(String codigoDane);
}
