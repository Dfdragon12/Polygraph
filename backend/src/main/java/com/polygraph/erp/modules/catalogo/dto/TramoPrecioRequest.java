package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TramoPrecioRequest(
        @NotNull(message = "La cantidad mínima es obligatoria")
        @Min(value = 2, message = "La cantidad mínima debe ser al menos 2")
        Integer cantidadMinima,

        @NotNull(message = "El valor unitario es obligatorio")
        @DecimalMin(value = "0.01", message = "El valor unitario debe ser mayor a 0")
        BigDecimal valorUnitario
) {}
