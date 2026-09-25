package com.polygraph.erp.modules.notificaciones.dto;

import java.util.List;

public record PaginaNotificacionesResponse(
        List<NotificacionResponse> items,
        boolean hayMas,
        long total
) {}
