package com.polygraph.erp.modules.auth.repository;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<Usuario> findByTokenActivacion(String token);
    Optional<Usuario> findByTokenResetPassword(String token);
    List<Usuario> findByRolIn(Collection<Rol> roles);
    List<Usuario> findTop8ByUltimoAccesoIsNotNullOrderByUltimoAccesoDesc();
    long countByActivoTrue();
    long countByActivoTrueAndRolIn(Collection<Rol> roles);
    List<Usuario> findByIdCliente(Integer idCliente);
    Optional<Usuario> findFirstByIdClienteAndRol(Integer idCliente, Rol rol);
}
