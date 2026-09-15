package com.polygraph.erp.modules.pagos.dto;

import java.math.BigDecimal;

public record ValidarCuponResponse(
        String mensaje,
        BigDecimal montoTotalOriginal,
        BigDecimal montoTotalConDescuento,
        BigDecimal montoDescuento
) {}
