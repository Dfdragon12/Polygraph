package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.SaldoServicioCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SaldoServicioClienteRepository extends JpaRepository<SaldoServicioCliente, Long> {
    Optional<SaldoServicioCliente> findByCliente_IdClienteAndProceso_IdProceso(Integer idCliente, Integer idProceso);
    List<SaldoServicioCliente> findByCliente_IdCliente(Integer idCliente);

    /**
     * Descuenta 1 unidad en UNA sentencia atómica con la condición de saldo (NS-03): la base bloquea la fila, y dos envíos a la vez no pueden gastar la misma
     * unidad. Devuelve 0 filas si ya no queda saldo (o no hay fila).
     */
    @Modifying(flushAutomatically = true)
    @Query("update SaldoServicioCliente s set s.cantidadConsumida = s.cantidadConsumida + 1, s.cantidadDisponible = s.cantidadDisponible - 1, s.fechaActualizacion = :ahora "
            + "where s.cliente.idCliente = :idCliente and s.proceso.idProceso = :idProceso and s.cantidadComprada - s.cantidadConsumida >= 1")
    int descontarUna(@Param("idCliente") Integer idCliente, @Param("idProceso") Integer idProceso, @Param("ahora") java.time.LocalDateTime ahora);

    /** Devuelve 1 unidad a la bolsa (cancelación): lo contrario de descontarUna; no baja de 0 consumidas. */
    @Modifying(flushAutomatically = true)
    @Query("update SaldoServicioCliente s set s.cantidadConsumida = s.cantidadConsumida - 1, s.cantidadDisponible = s.cantidadDisponible + 1, s.fechaActualizacion = :ahora "
            + "where s.cliente.idCliente = :idCliente and s.proceso.idProceso = :idProceso and s.cantidadConsumida >= 1")
    int devolverUna(@Param("idCliente") Integer idCliente, @Param("idProceso") Integer idProceso, @Param("ahora") java.time.LocalDateTime ahora);

    // Existe una bolsa para este cliente solo tras una compra aprobada (ver comentario de la
    // entidad) — se usa como señal de "ya compró" para AuthService.resolverPuedeSolicitar.
    boolean existsByCliente_IdCliente(Integer idCliente);

    // Usado por ClienteAdminService.cambiarTipoCliente para bloquear el paso a pospago mientras
    // el cliente todavía tenga saldo prepago sin usar (no se quiere dejarlo huérfano).
    boolean existsByCliente_IdClienteAndCantidadDisponibleGreaterThan(Integer idCliente, Integer cantidad);
}
