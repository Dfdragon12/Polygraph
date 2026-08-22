package com.polygraph.erp.modules.pagos.dto;

import java.time.LocalDateTime;

public record AuditoriaPagoResponse(
        String referencia,
        String tipoEvento,
        String generadoPorEmail,
        Long montoEnCentavos,
        String estadoAnterior,
        String estadoNuevo,
        String ipOrigen,
        Boolean checksumValido,
        String detalle,
        LocalDateTime fechaEvento
) {
}
