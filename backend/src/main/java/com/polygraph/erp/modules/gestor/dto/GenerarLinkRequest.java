package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.NotNull;

public record GenerarLinkRequest(
        @NotNull(message = "El ID del servicio es obligatorio")
        Integer idServicio
) {}
