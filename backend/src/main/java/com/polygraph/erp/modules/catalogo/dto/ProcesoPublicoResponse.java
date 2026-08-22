package com.polygraph.erp.modules.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProcesoPublicoResponse(
        Integer    idProceso,
        String     nombreProceso,
        String     descripcion,
        String     clasificacion,
        String     codigoClasificacion,
        BigDecimal valor,
        Integer    diasHabilesEntrega,
        List<TramoPrecioResponse> tramosPrecio,
        List<String> puntosClave,
        Boolean aplicaPrecioCiudad,
        List<PrecioCiudadResponse> preciosCiudad
) {}
