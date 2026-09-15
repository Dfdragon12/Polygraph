package com.polygraph.erp.modules.gestor.dto;

import java.time.LocalDateTime;

public record FranjaHorariaResponse(
        LocalDateTime inicio,
        LocalDateTime fin,
        boolean disponible,
        // Solo viene informado cuando disponible = false: qué la está ocupando.
        String ocupadoPor
) {}
