package com.polygraph.erp.modules.gestor.dto;

import java.time.LocalDateTime;

public record HistorialLlamadaResponse(
        Long id,
        String estadoConfirmacion,
        String notas,
        String actorNombre,
        LocalDateTime fechaLlamada
) {}
