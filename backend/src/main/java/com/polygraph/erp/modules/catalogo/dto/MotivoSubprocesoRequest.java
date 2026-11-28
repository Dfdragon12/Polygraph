package com.polygraph.erp.modules.catalogo.dto;

import com.polygraph.erp.shared.enums.TipoMotivo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MotivoSubprocesoRequest(
        @NotNull(message = "Indica para qué es el motivo") TipoMotivo tipo,
        @NotBlank(message = "El nombre del motivo es obligatorio")
        @Size(max = 150, message = "El motivo admite máximo 150 caracteres")
        String nombre,
        // El motivo "Otro": quien actúa escribe cuál
        Boolean requiereTexto,
        // null al crear = va al final de su lista
        Integer orden,
        Boolean activo
) {}
