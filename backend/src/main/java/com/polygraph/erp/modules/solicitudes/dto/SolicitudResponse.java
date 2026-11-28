package com.polygraph.erp.modules.solicitudes.dto;

import java.time.LocalDate;

public record SolicitudResponse(
        Integer idServicio,
        String cedulaEvaluado,
        String nombresEvaluado,
        String apellidosEvaluado,
        String cargo,
        String estado,
        LocalDate fechaSolicitud,
        LocalDate fechaEntregaEstimada,
        String proceso,
        // Estado de tiempo en días hábiles (null si está cancelada o sin fecha).
        com.polygraph.erp.shared.dto.TiempoServicio tiempo,
        // Los subprocesos del servicio con su estado (para el resumen «Pendiente 1 · Programando 2...» y para resaltar los del estado filtrado),
        // el avance (publicados sobre no cancelados) y la etiqueta «Publicado parcial» / «Estudio completo publicado» (null si no hay publicados).
        java.util.List<SubprocesoResumen> subprocesos,
        int publicados,
        int vigentes,
        String etiquetaPublicacion,
        // «Pendiente 1 · Programando 2 · ...» calculado en el servidor.
        java.util.List<com.polygraph.erp.modules.servicios.dto.ServicioSemaforoFila.ResumenEstado> resumen
) {
    public record SubprocesoResumen(String nombre, String estado) {}
}
