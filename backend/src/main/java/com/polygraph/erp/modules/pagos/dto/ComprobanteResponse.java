package com.polygraph.erp.modules.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Comprobante de compra interno — no es una factura electrónica DIAN válida (ver migración
 * V136). Mientras no se conecte un proveedor autorizado (ej. Factus), esViaDePrueba es siempre true. */
public record ComprobanteResponse(
        Integer idComprobante,
        String numero,
        LocalDateTime fechaEmision,
        Long idOrdenCompra,
        String referenciaOrden,
        BigDecimal montoTotal,
        List<ItemOrdenResponse> items,
        String nombrePagador,
        String telefonoPagador,
        String emailPagador,
        boolean esDePrueba,
        String codigoCuponAplicado,
        String nombreDescuentoAplicado,
        BigDecimal montoDescuentoTotal,
        BigDecimal montoRecargosPendientes,
        String nombreCliente,
        String nitCliente,
        String telefonoCliente,
        String direccionCliente,
        String ciudadCliente,
        String emailCliente
) {}
