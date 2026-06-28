package com.polygraph.erp.modules.catalogo.dto;

import java.math.BigDecimal;

public record TipoProgresoResponse(
        Integer    idTipoProgreso,
        String     nombreProgreso,
        String     descripcion,
        Integer    orden,
        Boolean    activo,
        BigDecimal valor
) {}
