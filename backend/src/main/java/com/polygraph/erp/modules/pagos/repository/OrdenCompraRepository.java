package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.OrdenCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Long> {
    Optional<OrdenCompra> findByReferencia(String referencia);
    Optional<OrdenCompra> findByIdOrdenCompraAndCliente_IdCliente(Long idOrdenCompra, Integer idCliente);
    Page<OrdenCompra> findByCliente_IdClienteOrderByFechaCreacionDesc(Integer idCliente, Pageable pageable);
}
