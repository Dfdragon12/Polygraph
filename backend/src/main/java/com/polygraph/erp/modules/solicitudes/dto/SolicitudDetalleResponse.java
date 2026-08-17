package com.polygraph.erp.modules.solicitudes.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record SolicitudDetalleResponse(
        Integer idServicio,
        String cedulaEvaluado,
        String nombresEvaluado,
        String apellidosEvaluado,
        String celularEvaluado,
        String emailEvaluado,
        String cargo,
        String notas,
        Integer idProceso,
        String proceso,
        String clasificacion,
        String estado,
        LocalDate fechaSolicitud,
        LocalTime horaSolicitud,
        LocalDate fechaEntregaEstimada,
        String linkEvaluado,
        List<HistorialResponse> historial,
        String nombreCliente,
        String nitCliente,
        String telefonoCliente,
        String emailCliente
) {
    public record HistorialResponse(
            String estadoAnterior,
            String estadoNuevo,
            LocalDateTime fechaCambio,
            String usuario,
            String observacion
    ) {}
}
