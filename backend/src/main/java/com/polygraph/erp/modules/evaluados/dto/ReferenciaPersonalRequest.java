package com.polygraph.erp.modules.evaluados.dto;

import jakarta.validation.constraints.NotBlank;

public record ReferenciaPersonalRequest(
        @NotBlank(message = "El nombre de la referencia es obligatorio")
        String nombre,

        // Relación o tipo de vínculo (amigo, excompañero de trabajo, vecino...) — no familiares
        @NotBlank(message = "El tipo de vínculo de la referencia es obligatorio")
        String parentesco,

        @NotBlank(message = "El teléfono de la referencia es obligatorio")
        String telefono,

        String tiempoConocimiento,
        Boolean confirmaNoFamiliar,
        Boolean habloUltimos30Dias
) {}
