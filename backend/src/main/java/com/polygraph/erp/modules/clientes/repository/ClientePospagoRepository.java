package com.polygraph.erp.modules.clientes.repository;

import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientePospagoRepository extends JpaRepository<ClientePospago, Integer> {
    Optional<ClientePospago> findByIdCliente(Integer idCliente);
}
