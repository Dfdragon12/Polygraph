package com.polygraph.erp.modules.catalogo.dto;

import com.polygraph.erp.shared.enums.NivelCiudad;
import jakarta.validation.constraints.NotNull;

public record ClasificarCiudadRequest(
        @NotNull(message = "El nivel es obligatorio")
        NivelCiudad nivelCiudad,
        /** TP-08: si viene, marca o desmarca la ciudad como sede; si no viene, no se toca. */
        Boolean esSede
) {}
