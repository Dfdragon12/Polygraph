package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record AsignarRequest(
        @NotNull Long idUsuarioAsignado,
        @NotNull LocalDateTime fechaProgramada,
        String observaciones,
        // Si viene null, se usa el estimado del catálogo (tipoProgreso.minutosEstimados).
        Integer duracionMinutos
) {}
