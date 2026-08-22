package com.polygraph.erp.modules.catalogo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DescuentoResponse(
        Long idDescuento,
        String nombre,
        String codigo,
        String tipo,
        BigDecimal valor,
        String alcance,
        Integer idClasificacion,
        String nombreClasificacion,
        Integer idProceso,
        String nombreProceso,
        BigDecimal montoMaximoDescuento,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        Boolean activo,
        LocalDateTime fechaCreacion
) {
}
