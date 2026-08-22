package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record AsignarRequest(
        @NotNull Long idUsuarioAsignado,
        LocalDateTime fechaProgramada,
        String observaciones
) {}
