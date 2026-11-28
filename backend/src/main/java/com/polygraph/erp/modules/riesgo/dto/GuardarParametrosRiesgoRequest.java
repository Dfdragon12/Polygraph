package com.polygraph.erp.modules.riesgo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record GuardarParametrosRiesgoRequest(
        @NotEmpty @Valid List<Umbral> umbrales,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal umbralServicioAlto,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal umbralServicioModerado,
        @NotNull @Min(value = 1, message = "La cantidad de servicios moderados debe ser al menos 1.") Integer cantidadServiciosModerados
) {
    public record Umbral(
            @NotBlank String criticidad,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal limiteBajo,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal limiteMedio,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal minimoMedio) {}
}
