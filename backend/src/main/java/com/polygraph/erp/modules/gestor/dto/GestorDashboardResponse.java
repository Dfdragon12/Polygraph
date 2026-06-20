package com.polygraph.erp.modules.gestor.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record GestorDashboardResponse(
        long pendientes,
        long programando,
        long enEjecucion,
        long finalizados,
        long publicados,
        long cancelados,
        long total,
        List<SolicitudResumen> recientes
) {
    public record SolicitudResumen(
            Long idSolicitud,
            String cedulaEvaluado,
            String nombresEvaluado,
            String apellidosEvaluado,
            String cargo,
            String estado,
            LocalDateTime fechaSolicitud,
            LocalDate fechaEntregaEstimada,
            String nombreCliente,
            List<String> servicios
    ) {}
}
