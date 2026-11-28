package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.ComprobanteCompra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ComprobanteCompraRepository extends JpaRepository<ComprobanteCompra, Integer> {
    Optional<ComprobanteCompra> findByOrdenCompra_IdOrdenCompra(Long idOrdenCompra);

    List<ComprobanteCompra> findByOrdenCompra_Cliente_IdClienteOrderByFechaEmisionDesc(Integer idCliente);

    boolean existsByOrdenCompra_IdOrdenCompra(Long idOrdenCompra);
}
