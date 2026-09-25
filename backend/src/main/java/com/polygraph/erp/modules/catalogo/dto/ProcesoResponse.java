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
        List<TramoPrecioResponse>     tramosPrecio,
        Boolean                       aplicaPrecioCiudad,
        List<PrecioCiudadResponse>    preciosCiudad,
        List<String>                  puntosClave,
        // Calificación de riesgo (ver CalificacionProcesoService): suma de pesos de los
        // subprocesos que cuentan, si ya califica (suma 100 y ninguno sin peso) y por qué no.
        BigDecimal                    sumaPesos,
        boolean                       calificaRiesgo,
        String                        motivoNoCalifica,
        String                        criticidadDefecto,
        BigDecimal                    coberturaMinima
) {}
