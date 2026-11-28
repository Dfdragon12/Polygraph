package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.NotNull;

public record ConfirmarAsistenciaRequest(
        @NotNull String estadoConfirmacion,
        String notas
) {}
