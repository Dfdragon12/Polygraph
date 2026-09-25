package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.FacturaPospago;
import com.polygraph.erp.shared.enums.EstadoFacturaPospago;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FacturaPospagoRepository extends JpaRepository<FacturaPospago, Long> {
    List<FacturaPospago> findByCliente_IdClienteOrderByFechaGeneracionDesc(Integer idCliente);

    Optional<FacturaPospago> findByReferencia(String referencia);

    Page<FacturaPospago> findByEstadoOrderByFechaGeneracionDesc(EstadoFacturaPospago estado, Pageable pageable);

    Page<FacturaPospago> findAllByOrderByFechaGeneracionDesc(Pageable pageable);

    List<FacturaPospago> findByEstadoAndFechaVencimientoBefore(EstadoFacturaPospago estado, LocalDate fecha);

    // Usado por ClienteAdminService.cambiarTipoCliente para bloquear el paso a prepago mientras
    // el cliente tenga facturas pospago sin pagar.
    boolean existsByCliente_IdClienteAndEstadoIn(Integer idCliente, Collection<EstadoFacturaPospago> estados);

    // Para la barra de resumen "Pagado / Pendiente / Vencido" de Facturacion.jsx — ver
    // ClientePospagoService.resumenFacturas.
    @Query("select coalesce(sum(f.montoTotal), 0) from FacturaPospago f where f.estado = :estado")
    BigDecimal sumMontoTotalByEstado(@Param("estado") EstadoFacturaPospago estado);
}
