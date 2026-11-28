package com.polygraph.erp.modules.formularios.repository;

import com.polygraph.erp.modules.formularios.entity.Formulario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FormularioRepository extends JpaRepository<Formulario, Integer> {
    Optional<Formulario> findByServicioSubproceso_Id(Long idServicioSubproceso);
}
