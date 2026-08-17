package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.OrdenCompraItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrdenCompraItemRepository extends JpaRepository<OrdenCompraItem, Long> {
    List<OrdenCompraItem> findByOrdenCompra_IdOrdenCompra(Long idOrdenCompra);
}
