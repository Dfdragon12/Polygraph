package com.polygraph.erp.modules.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProcesoResponse(
        Integer                       idProceso,
        String                        nombreProceso,
        String                        descripcion,
        Boolean                       activo,
        long                          totalPasos,
        ClasificacionProcesoResponse  clasificacion,
        BigDecimal                    valor,
        BigDecimal                    valorCalculado,
        Integer                       diasHabilesEntrega,
        List<TramoPrecioResponse>     tramosPrecio
) {}
