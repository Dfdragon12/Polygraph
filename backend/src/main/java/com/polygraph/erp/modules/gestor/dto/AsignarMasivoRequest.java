package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record AsignarMasivoRequest(
        @NotEmpty List<Long> ids,
        @NotNull Long idUsuarioAsignado,
        LocalDateTime fechaProgramada,
        String observaciones
) {}
