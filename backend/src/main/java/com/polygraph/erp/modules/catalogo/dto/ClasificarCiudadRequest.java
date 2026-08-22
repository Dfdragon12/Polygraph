package com.polygraph.erp.modules.catalogo.dto;

import com.polygraph.erp.shared.enums.NivelCiudad;
import jakarta.validation.constraints.NotNull;

public record ClasificarCiudadRequest(
        @NotNull(message = "El nivel es obligatorio")
        NivelCiudad nivelCiudad
) {}
