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
        List<SubprocesoResumen> subprocesos,
        // null = el servicio no tiene candidato asociado (no aplica ninguna restricción — ver
        // AsignacionService.validarCandidatoCompleto); false = el candidato todavía no ha
        // diligenciado su link, así que el frontend debe deshabilitar "Asignar"/"Asignar programador".
        Boolean candidatoCompletado
) {
    // programadorAsignado: solo relevante para subprocesos de poligrafía/visita — si el gestor ya
    // le encargó a un PROGRAMADOR coordinarlo (ver ServicioSubproceso.programadorResponsable).
    public record SubprocesoResumen(String nombre, String estado, boolean programadorAsignado) {}
}
