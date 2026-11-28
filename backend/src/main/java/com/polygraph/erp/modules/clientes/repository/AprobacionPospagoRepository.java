package com.polygraph.erp.modules.clientes.repository;

import com.polygraph.erp.modules.clientes.entity.AprobacionPospago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AprobacionPospagoRepository extends JpaRepository<AprobacionPospago, Long> {
    Optional<AprobacionPospago> findFirstByIdClienteOrderByAprobadoEnDesc(Integer idCliente);
}
