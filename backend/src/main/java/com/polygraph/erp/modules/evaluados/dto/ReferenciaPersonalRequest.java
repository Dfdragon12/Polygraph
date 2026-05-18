package com.polygraph.erp.modules.evaluados.dto;

import jakarta.validation.constraints.NotBlank;

public record ReferenciaPersonalRequest(
        @NotBlank(message = "El nombre de la referencia es obligatorio")
        String nombre,

        String parentesco,
        String telefono,
        String tiempoConocimiento
) {}
