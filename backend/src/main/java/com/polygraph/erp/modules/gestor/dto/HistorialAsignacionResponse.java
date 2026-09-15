package com.polygraph.erp.modules.gestor.dto;

import java.time.LocalDateTime;

public record HistorialAsignacionResponse(
        Long id,
        String accion,
        String usuarioAnteriorNombre,
        String usuarioNuevoNombre,
        LocalDateTime fechaProgramadaAnterior,
        LocalDateTime fechaProgramadaNueva,
        String actorNombre,
        String observaciones,
        LocalDateTime fechaEvento
) {}
