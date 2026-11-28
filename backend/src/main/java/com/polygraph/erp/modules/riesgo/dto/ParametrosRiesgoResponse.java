package com.polygraph.erp.modules.riesgo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Configuración global del motor: umbrales por criticidad + parámetros de las reglas de piso. */
public record ParametrosRiesgoResponse(
        List<Umbral> umbrales,
        BigDecimal umbralServicioAlto,
        BigDecimal umbralServicioModerado,
        Integer cantidadServiciosModerados,
        Integer version,
        String actualizadoPorNombre,
        LocalDateTime fechaActualizacion
) {
    public record Umbral(String criticidad, BigDecimal limiteBajo, BigDecimal limiteMedio, BigDecimal minimoMedio) {}
}
