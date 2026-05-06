package com.polygraph.erp.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "El token es requerido")
        String token,

        @NotBlank(message = "La contraseña es requerida")
        @Size(min = 8, max = 50, message = "Mínimo 8 caracteres")
        String nuevaPassword
) {}
