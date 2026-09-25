package com.polygraph.erp.modules.pagos.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record GenerarFacturaPospagoRequest(
        @NotNull LocalDate fechaVencimiento,
        // Opcional: si viene vacío o null, se factura TODO el consumo pendiente del cliente (como
        // siempre); si trae ids puntuales, solo esos movimientos entran en esta factura y el resto
        // queda pendiente para un corte posterior.
        List<Long> idsMovimientos
) {}
