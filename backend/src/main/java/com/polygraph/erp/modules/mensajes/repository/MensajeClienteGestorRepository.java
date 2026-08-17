package com.polygraph.erp.modules.mensajes.repository;

import com.polygraph.erp.modules.mensajes.entity.MensajeClienteGestor;
import com.polygraph.erp.shared.enums.OrigenMensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MensajeClienteGestorRepository extends JpaRepository<MensajeClienteGestor, Long> {

    List<MensajeClienteGestor> findByCliente_IdClienteAndFechaExpiracionAfterOrderByFechaEnvioAsc(
            Integer idCliente, LocalDateTime ahora);

    Optional<MensajeClienteGestor> findTopByCliente_IdClienteAndFechaExpiracionAfterOrderByFechaEnvioDesc(
            Integer idCliente, LocalDateTime ahora);

    long countByCliente_IdClienteAndOrigenAndLeidoFalseAndFechaExpiracionAfter(
            Integer idCliente, OrigenMensaje origen, LocalDateTime ahora);

    @Modifying
    @Transactional
    @Query("update MensajeClienteGestor m set m.leido = true " +
           "where m.cliente.idCliente = :idCliente and m.origen = :origen and m.leido = false")
    int marcarLeidos(@Param("idCliente") Integer idCliente, @Param("origen") OrigenMensaje origen);

    int deleteByFechaExpiracionBefore(LocalDateTime ahora);
}
