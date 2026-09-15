package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.MovimientoCreditoPospago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoCreditoPospagoRepository extends JpaRepository<MovimientoCreditoPospago, Long> {
    List<MovimientoCreditoPospago> findByCliente_IdClienteAndFacturaIsNullOrderByFechaGeneradoAsc(Integer idCliente);

    List<MovimientoCreditoPospago> findByFactura_IdFacturaOrderByFechaGeneradoAsc(Long idFactura);
}
