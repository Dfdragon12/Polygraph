package com.polygraph.erp.modules.gestor.dto;

import java.time.LocalDateTime;
import java.util.List;

public record NovedadSubprocesoResponse(
        Long id,
        String tipo,
        String tipoEtiqueta,
        String categoria,
        String categoriaEtiqueta,
        String categoriaColor,
        String descripcion,
        Long idRegistradoPor,
        String registradoPorNombre,
        LocalDateTime fechaRegistro,
        String estado,
        String respuesta,
        String resueltaPorNombre,
        LocalDateTime fechaResolucion,
        // Lo que pasó después de reportarla, en orden: observaciones y reprogramaciones.
        List<SeguimientoResponse> seguimientos
) {
    public record SeguimientoResponse(
            Long id,
            String tipo,
            String texto,
            LocalDateTime fechaAnterior,
            LocalDateTime fechaNueva,
            String autorNombre,
            String autorRol,
            LocalDateTime fecha) {}
}
