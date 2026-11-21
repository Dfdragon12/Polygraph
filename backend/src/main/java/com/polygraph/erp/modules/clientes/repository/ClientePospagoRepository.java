package com.polygraph.erp.modules.clientes.repository;

import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientePospagoRepository extends JpaRepository<ClientePospago, Integer> {
    Optional<ClientePospago> findByIdCliente(Integer idCliente);

    /** TP-12: la fila del cupo con bloqueo de escritura, para descontar: dos solicitudes a la vez no pueden superar el cupo. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from ClientePospago p where p.idCliente = :idCliente")
    Optional<ClientePospago> bloquearPorIdCliente(@org.springframework.data.repository.query.Param("idCliente") Integer idCliente);
}
