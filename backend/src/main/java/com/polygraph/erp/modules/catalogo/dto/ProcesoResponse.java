package com.polygraph.erp.modules.catalogo.dto;

import java.math.BigDecimal;

public record ProcesoResponse(
        Integer                       idProceso,
        String                        nombreProceso,
        String                        descripcion,
        Boolean                       activo,
        long                          totalPasos,
        ClasificacionProcesoResponse  clasificacion,
        BigDecimal                    valor,
        BigDecimal                    valorCalculado
) {}
