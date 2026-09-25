package com.polygraph.erp.modules.pagos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record GenerarNotaAjusteRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal monto,
        @NotBlank String motivo,
        String observaciones
) {}
