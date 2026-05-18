package com.polygraph.erp.modules.servicios.dto;

import com.polygraph.erp.shared.enums.CategoriaServicio;

import java.math.BigDecimal;

public record CatalogoServicioResponse(
    Integer idCatalogo,
    String nombre,
    String descripcion,
    CategoriaServicio categoria,
    BigDecimal precioBase,
    Boolean activo,
    Integer orden
) {}
