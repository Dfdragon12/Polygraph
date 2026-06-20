package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TipoProgresoRequest(
        @NotBlank @Size(max = 100) String nombreProgreso,
        String descripcion,
        Integer orden,
        BigDecimal valor
) {}
