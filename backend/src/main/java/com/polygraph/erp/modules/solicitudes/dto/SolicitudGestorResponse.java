package com.polygraph.erp.modules.solicitudes.dto;

import java.time.LocalDate;
import java.util.List;

public record SolicitudGestorResponse(
        Integer idServicio,
        String cedulaEvaluado,
        String nombresEvaluado,
        String apellidosEvaluado,
        String cargo,
        String estado,
        LocalDate fechaSolicitud,
        LocalDate fechaEntregaEstimada,
        String proceso,
        List<SubprocesoResumen> subprocesos
) {
    public record SubprocesoResumen(String nombre, String estado) {}
}
