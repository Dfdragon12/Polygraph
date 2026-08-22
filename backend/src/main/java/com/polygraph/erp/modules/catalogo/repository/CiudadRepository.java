package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CiudadRepository extends JpaRepository<Ciudad, Integer> {
    List<Ciudad> findAllByOrderByNombreCiudadAsc();

    Optional<Ciudad> findFirstByNombreCiudadIgnoreCaseAndDepartamentoIgnoreCase(String nombreCiudad, String departamento);
}
