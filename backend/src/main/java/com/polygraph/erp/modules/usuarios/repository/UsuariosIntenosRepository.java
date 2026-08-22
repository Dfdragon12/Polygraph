package com.polygraph.erp.modules.usuarios.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.polygraph.erp.modules.usuarios.entity.UsuariosInternos;

import java.util.List;
import java.util.Optional;

public interface UsuariosIntenosRepository extends JpaRepository<UsuariosInternos, Integer> {
    Optional<UsuariosInternos> findByEmail(String email);
    List<UsuariosInternos> findBySubprocesosAsignados_IdTipoProgresoAndActivoTrue(Integer idTipoProgreso);
}