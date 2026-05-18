package com.polygraph.erp.modules.empleados.repository;

import com.polygraph.erp.modules.empleados.entity.Empleados;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmpleadoRepository extends JpaRepository<Empleados, Integer> {
    Optional<Empleados> findByEmail(String email);
}