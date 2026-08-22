package com.polygraph.erp.modules.clientes.repository;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {
    boolean existsByNit(String nit);
    boolean existsByEmailPrincipal(String email);
    Optional<Cliente> findByEmailPrincipal(String email);
    List<Cliente> findByIdGestor(Long idGestor);
}
