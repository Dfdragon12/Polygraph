package com.polygraph.erp.modules.catalogo.dto;

import java.math.BigDecimal;

public record TramoPrecioResponse(
        Integer    idTramo,
        Integer    cantidadMinima,
        BigDecimal valorUnitario
) {}
