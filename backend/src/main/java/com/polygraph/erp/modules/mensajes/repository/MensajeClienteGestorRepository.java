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

    List<MensajeClienteGestor> findByUsuarioCliente_IdUsuarioAndFechaExpiracionAfterOrderByFechaEnvioAsc(
            Long idUsuarioCliente, LocalDateTime ahora);

    Optional<MensajeClienteGestor> findTopByUsuarioCliente_IdUsuarioAndFechaExpiracionAfterOrderByFechaEnvioDesc(
            Long idUsuarioCliente, LocalDateTime ahora);

    long countByUsuarioCliente_IdUsuarioAndOrigenAndLeidoFalseAndFechaExpiracionAfter(
            Long idUsuarioCliente, OrigenMensaje origen, LocalDateTime ahora);

    @Modifying
    @Transactional
    @Query("update MensajeClienteGestor m set m.leido = true " +
           "where m.usuarioCliente.idUsuario = :idUsuarioCliente and m.origen = :origen and m.leido = false")
    int marcarLeidos(@Param("idUsuarioCliente") Long idUsuarioCliente, @Param("origen") OrigenMensaje origen);

    int deleteByFechaExpiracionBefore(LocalDateTime ahora);
}
