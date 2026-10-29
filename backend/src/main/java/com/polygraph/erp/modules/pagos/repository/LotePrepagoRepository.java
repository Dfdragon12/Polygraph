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

    Optional<LotePrepago> findByItem_IdItem(Long idItem);
}
