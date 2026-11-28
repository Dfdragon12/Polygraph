package com.polygraph.erp.modules.usuarios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarPasswordUsuarioInternoRequest(
        @NotBlank(message = "La contraseña es requerida")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password,
        // Confirmación de identidad: la contraseña del ADMIN que hace el cambio, no la del usuario
        // al que se le resetea — para que no baste con tener la sesión abierta para tomar el
        // control de cualquier cuenta interna.
        @NotBlank(message = "Debes confirmar tu contraseña")
        String claveAdmin
) {
}
