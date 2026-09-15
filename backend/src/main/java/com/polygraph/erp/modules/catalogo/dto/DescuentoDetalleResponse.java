package com.polygraph.erp.modules.catalogo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Desglose completo de un descuento — quién puede usarlo y quién ya lo usó, para la vista de detalle del admin. */
public record DescuentoDetalleResponse(
        DescuentoResponse descuento,
        List<UsoResponse> usos
) {
    public record UsoResponse(
            Integer idCliente,
            String nombreCliente,
            String emailCliente,
            LocalDateTime fechaUso,
            Long idOrdenCompra,
            String referenciaOrden,
            String estadoOrden,
            BigDecimal montoOrden
    ) {}
}
