package com.polygraph.erp.modules.usuarios.repository;

import com.polygraph.erp.modules.usuarios.entity.EmpleadoCiudad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmpleadoCiudadRepository extends JpaRepository<EmpleadoCiudad, Integer> {

    List<EmpleadoCiudad> findByEmpleado_IdEmpleado(Integer idEmpleado);

    // Base para "quién puede atender un servicio en la ciudad X" (poligrafistas/visitadores que
    // cubren esa ciudad) cuando se implemente la asignación automática por ciudad.
    List<EmpleadoCiudad> findByCiudad_IdCiudad(Integer idCiudad);
}
