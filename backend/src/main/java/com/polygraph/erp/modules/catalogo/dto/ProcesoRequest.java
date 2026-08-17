package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProcesoRequest(
        @NotBlank @Size(max = 100) String nombreProceso,
        String descripcion,
        @NotNull Integer idClasificacion,
        BigDecimal valor,
        @Min(1) @Max(365) Integer diasHabilesEntrega
) {}
