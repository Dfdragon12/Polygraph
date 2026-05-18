package com.polygraph.erp.modules.solicitudes.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SolicitudDetalleResponse(
        Long idSolicitud,
        String cedulaEvaluado,
        String nombresEvaluado,
        String apellidosEvaluado,
        String celularEvaluado,
        String emailEvaluado,
        String ciudadEvaluado,
        String cargo,
        String notas,
        String estado,
        LocalDateTime fechaSolicitud,
        LocalDate fechaEntregaEstimada,
        String linkEvaluado,
        List<ServicioItemResponse> servicios,
        List<HistorialResponse> historial
) {
    public record ServicioItemResponse(
            Integer idCatalogo,
            String nombre,
            String categoria,
            String estado
    ) {}

    public record HistorialResponse(
            String estadoAnterior,
            String estadoNuevo,
            LocalDateTime fechaCambio,
            String usuario,
            String observacion
    ) {}
}
