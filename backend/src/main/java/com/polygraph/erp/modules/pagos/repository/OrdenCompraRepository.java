package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.OrdenCompra;
import com.polygraph.erp.shared.enums.EstadoOrdenCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Long> {
    Optional<OrdenCompra> findByReferencia(String referencia);
    Optional<OrdenCompra> findByIdOrdenCompraAndCliente_IdCliente(Long idOrdenCompra, Integer idCliente);
    Page<OrdenCompra> findByCliente_IdClienteOrderByFechaCreacionDesc(Integer idCliente, Pageable pageable);
    Page<OrdenCompra> findByCliente_IdClienteAndEstadoOrderByFechaCreacionDesc(Integer idCliente, EstadoOrdenCompra estado, Pageable pageable);
    Page<OrdenCompra> findAllByOrderByFechaCreacionDesc(Pageable pageable);
    Page<OrdenCompra> findByEstadoOrderByFechaCreacionDesc(EstadoOrdenCompra estado, Pageable pageable);
    List<OrdenCompra> findByEstadoAndFechaCreacionBefore(EstadoOrdenCompra estado, LocalDateTime fechaLimite);
}
