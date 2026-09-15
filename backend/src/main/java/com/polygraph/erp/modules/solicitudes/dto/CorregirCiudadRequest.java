package com.polygraph.erp.modules.solicitudes.dto;

import jakarta.validation.constraints.NotNull;

public record CorregirCiudadRequest(
        @NotNull Integer idCiudad
) {}
