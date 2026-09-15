package com.polygraph.erp.modules.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FacturaPospagoResponse(
        Long idFactura,
        Integer idCliente,
        String referencia,
        LocalDate fechaCorte,
        LocalDate fechaVencimiento,
        BigDecimal montoTotal,
        String estado,
        LocalDateTime fechaGeneracion,
        LocalDateTime fechaPago,
        String nombreCliente
) {}
