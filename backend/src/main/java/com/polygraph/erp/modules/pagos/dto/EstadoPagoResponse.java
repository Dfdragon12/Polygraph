package com.polygraph.erp.modules.pagos.dto;

import java.time.LocalDateTime;

public record EstadoPagoResponse(
        String referencia,
        Long montoEnCentavos,
        String moneda,
        String estado,
        String metodoPago,
        String descripcion,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion
) {
}
