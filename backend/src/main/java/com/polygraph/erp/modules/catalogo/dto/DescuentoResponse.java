package com.polygraph.erp.modules.catalogo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        LocalDateTime fechaCreacion,
        String publicoObjetivo,
        List<ClienteElegibleResponse> clientesElegibles,
        long vecesUsado
) {
    public record ClienteElegibleResponse(Integer idCliente, String nombreCliente) {}
}
