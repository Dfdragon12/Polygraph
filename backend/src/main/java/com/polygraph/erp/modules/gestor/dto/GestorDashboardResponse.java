package com.polygraph.erp.modules.gestor.dto;

import java.time.LocalDate;
import java.util.List;

public record GestorDashboardResponse(
        long pendientes,
        long programando,
        long enEjecucion,
        long finalizados,
        long publicados,
        long cancelados,
        long reprogramados,
        long total,
        List<SolicitudResumen> recientes,
        List<SolicitudResumen> pendientesPorEntrega,
        List<LinkCandidatoResponse> linksPorAtender
) {
    public record SolicitudResumen(
            Integer idServicio,
            String cedulaEvaluado,
            String nombresEvaluado,
            String apellidosEvaluado,
            String cargo,
            String estado,
            LocalDate fechaSolicitud,
            LocalDate fechaEntregaEstimada,
            String nombreCliente,
            String proceso
    ) {}
}
