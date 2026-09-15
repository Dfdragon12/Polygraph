package com.polygraph.erp.modules.pagos.dto;

import java.math.BigDecimal;

public record ItemOrdenResponse(
        Integer idProceso,
        String nombreProceso,
        Integer cantidad,
        BigDecimal valorOriginal,
        BigDecimal valorUnitario,
        BigDecimal subtotal,
        String nombreDescuentoAplicado
) {}
