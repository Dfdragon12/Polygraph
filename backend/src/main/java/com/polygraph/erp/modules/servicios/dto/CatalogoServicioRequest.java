package com.polygraph.erp.modules.servicios.dto;

import com.polygraph.erp.shared.enums.CategoriaServicio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CatalogoServicioRequest(
    @NotBlank String nombre,
    String descripcion,
    @NotNull CategoriaServicio categoria,
    BigDecimal precioBase,
    Integer orden
) {}
