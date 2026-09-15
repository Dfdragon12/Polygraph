package com.polygraph.erp.modules.solicitudes.dto;

// Conteos por estado del semáforo — alimenta las tarjetas de resumen de "Mis Solicitudes" en el
// portal del cliente (mismo shape que com.polygraph.erp.modules.gestor.dto.ConteoEstadosResponse,
// duplicado a propósito para no acoplar el módulo de solicitudes al de gestor).
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
