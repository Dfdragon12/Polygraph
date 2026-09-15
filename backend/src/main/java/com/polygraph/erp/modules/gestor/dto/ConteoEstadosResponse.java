package com.polygraph.erp.modules.gestor.dto;

// Conteos por estado del semáforo — alimenta las tarjetas de resumen de "Solicitudes" en el
// portal del gestor (sin traer las listas pesadas de servicios recientes/pendientes que sí trae
// GestorDashboardResponse).
public record ConteoEstadosResponse(
        long pendientes,
        long programando,
        long enEjecucion,
        long finalizados,
        long publicados,
        long cancelados,
        long reprogramados,
        long total
) {}
