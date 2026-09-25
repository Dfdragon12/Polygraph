package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.OrdenCompra;
import com.polygraph.erp.shared.enums.EstadoOrdenCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

    /** Para el panel "Prepago — sin facturar" de Facturacion.jsx: órdenes ya aprobadas (cobradas)
     *  a las que todavía no se les generó comprobante — ver OrdenCompraService.
     *  listarOrdenesSinFacturar. La generación es manual, así que estas se acumulan hasta que un
     *  admin las procese. */
    @Query("select o from OrdenCompra o where o.estado = 'APROBADA' and not exists "
            + "(select 1 from ComprobanteCompra cc where cc.ordenCompra = o) order by o.fechaCreacion asc")
    Page<OrdenCompra> findAprobadasSinComprobante(Pageable pageable);

    // Para la barra de resumen "Pagado / Pendiente / Vencido" de Facturacion.jsx — ver
    // OrdenCompraService.resumenPagos.
    @Query("select coalesce(sum(o.montoTotal), 0) from OrdenCompra o where o.estado = :estado")
    BigDecimal sumMontoTotalByEstado(@Param("estado") EstadoOrdenCompra estado);
}
