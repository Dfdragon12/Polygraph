package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.SaldoServicioCliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SaldoServicioClienteRepository extends JpaRepository<SaldoServicioCliente, Long> {
    Optional<SaldoServicioCliente> findByCliente_IdClienteAndProceso_IdProceso(Integer idCliente, Integer idProceso);
    List<SaldoServicioCliente> findByCliente_IdCliente(Integer idCliente);
    // Existe una bolsa para este cliente solo tras una compra aprobada (ver comentario de la
    // entidad) — se usa como señal de "ya compró" para AuthService.resolverPuedeSolicitar.
    boolean existsByCliente_IdCliente(Integer idCliente);

    // Usado por ClienteAdminService.cambiarTipoCliente para bloquear el paso a pospago mientras
    // el cliente todavía tenga saldo prepago sin usar (no se quiere dejarlo huérfano).
    boolean existsByCliente_IdClienteAndCantidadDisponibleGreaterThan(Integer idCliente, Integer cantidad);
}
