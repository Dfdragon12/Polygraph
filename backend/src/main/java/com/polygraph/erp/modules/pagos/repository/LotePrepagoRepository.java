package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.LotePrepago;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LotePrepagoRepository extends JpaRepository<LotePrepago, Long> {

    /** Los lotes del cliente y servicio, del más antiguo al más nuevo (el orden FIFO). */
    List<LotePrepago> findByCliente_IdClienteAndProceso_IdProcesoOrderByFechaCompraAscIdLoteAsc(Integer idCliente, Integer idProceso);

    /** Los mismos lotes con bloqueo, para descontar (NS-03). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LotePrepago l where l.cliente.idCliente = :idCliente and l.proceso.idProceso = :idProceso order by l.fechaCompra, l.idLote")
    List<LotePrepago> bloquearPorClienteYProceso(@Param("idCliente") Integer idCliente, @Param("idProceso") Integer idProceso);

    /** Los lotes de un ítem de compra (uno por paquete). */
    List<LotePrepago> findByItem_IdItem(Long idItem);

    boolean existsByItem_IdItem(Long idItem);

    /** TP-09: todos los lotes del cliente (para el vencimiento más próximo por servicio). */
    List<LotePrepago> findByCliente_IdCliente(Integer idCliente);

    /** TP-08: lotes de una cobertura (p. ej. NACIONAL) de todos los servicios del cliente. */
    List<LotePrepago> findByCliente_IdClienteAndCobertura(Integer idCliente, String cobertura);

    boolean existsByIdTransaccionPagoAndItem_IdItem(String idTransaccionPago, Long idItem);

    /** TP-07: lotes con vencimiento ya pasado cuyas unidades todavía no se pasaron a «vencidas» (lo recorre el job). */
    @Query("select l from LotePrepago l where l.fechaVencimiento is not null and l.fechaVencida is null and l.fechaVencimiento < :ahora and l.cantidadComprada > l.cantidadConsumida")
    List<LotePrepago> pendientesDeVencer(@Param("ahora") java.time.LocalDateTime ahora);

    /** Lotes del cliente con unidades que vencen en la ventana [ahora, limite] (alerta del Dashboard y notificación). */
    @Query("select l from LotePrepago l where l.cliente.idCliente = :idCliente and l.fechaVencimiento is not null and l.fechaVencida is null "
            + "and l.fechaVencimiento >= :ahora and l.fechaVencimiento <= :limite and l.cantidadComprada - l.cantidadConsumida - l.cantidadVencida > 0 order by l.fechaVencimiento")
    List<LotePrepago> porVencerDelCliente(@Param("idCliente") Integer idCliente, @Param("ahora") java.time.LocalDateTime ahora, @Param("limite") java.time.LocalDateTime limite);

    /** Todos los lotes que vencen en la ventana y todavía no se avisaron (para la notificación, una vez por lote). */
    @Query("select l from LotePrepago l where l.fechaVencimiento is not null and l.fechaVencida is null and l.avisoVencimientoEn is null "
            + "and l.fechaVencimiento >= :ahora and l.fechaVencimiento <= :limite and l.cantidadComprada - l.cantidadConsumida - l.cantidadVencida > 0")
    List<LotePrepago> porAvisar(@Param("ahora") java.time.LocalDateTime ahora, @Param("limite") java.time.LocalDateTime limite);

    /** Reporte de ingreso por vencimiento: lotes que perdieron unidades entre dos fechas. */
    @Query("select l from LotePrepago l where l.cantidadVencida > 0 and l.fechaVencida >= :desde and l.fechaVencida < :hasta order by l.fechaVencida")
    List<LotePrepago> vencidosEntre(@Param("desde") java.time.LocalDateTime desde, @Param("hasta") java.time.LocalDateTime hasta);
}
