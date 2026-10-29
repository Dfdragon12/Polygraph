package com.polygraph.erp.modules.gestor.dto;

// Conteos por estado de asignación — alimenta las tarjetas de resumen del módulo de
// Programación (Servicios/Agenda) tanto para GESTOR (sin restricción) como para PROGRAMADOR
// (ya viene filtrado a poligrafía/visita por el mismo alcance que usa "listar").
public record ConteoAsignacionesResponse(
        long pendientes,
        long asignados,
        long completados,
        long publicados,
        long cancelados,
        long total,
        // Solo "Por hacer" de quien ejecuta (0 en el resto): cuántas tienen cada indicador, para los filtros.
        long conDocumentosPorRevisar,
        long conDevoluciones,
        long conNovedadesAbiertas,
        long porVencer
) {}
