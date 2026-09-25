package com.polygraph.erp.modules.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrdenCompraResponse(
        Long idOrdenCompra,
        String referencia,
        BigDecimal montoTotal,
        String estado,
        Boolean modoSimulado,
        String urlCheckout,
        List<ItemOrdenResponse> items,
        LocalDateTime fechaCreacion,
        String metodoPago,
        String numeroAprobacion,
        String idTransaccionPasarela,
        String nombrePagador,
        String telefonoPagador,
        String emailPagador,
        Integer idCliente,
        String nombreCliente,
        Integer idComprobante,
        String numeroComprobante,
        String codigoCuponAplicado,
        String nombreDescuentoAplicado,
        BigDecimal montoDescuentoTotal,
        BigDecimal montoRecargosPendientes
) {}
