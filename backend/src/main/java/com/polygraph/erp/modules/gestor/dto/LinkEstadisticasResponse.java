package com.polygraph.erp.modules.gestor.dto;

public record LinkEstadisticasResponse(
        long total,
        long pendientes,
        long usados,
        long expirados,
        long bloqueados
) {}
