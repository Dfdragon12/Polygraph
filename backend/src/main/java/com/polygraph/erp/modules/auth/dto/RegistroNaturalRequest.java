package com.polygraph.erp.modules.auth.dto;

import jakarta.validation.constraints.*;

public record RegistroNaturalRequest(
        @NotBlank(message = "El nombre es requerido")
        @Size(max = 100)
        String nombre,

        @NotBlank(message = "El apellido es requerido")
        @Size(max = 100)
        String apellido,

        @NotBlank(message = "El email es requerido")
        @Email(message = "Email inválido")
        @Size(max = 150)
        String email,

        @NotBlank(message = "La contraseña es requerida")
        @Size(min = 8, max = 50, message = "Mínimo 8 caracteres")
        String password,

        @Size(max = 20)
        String telefono,

        @NotNull(message = "Debe aceptar los términos y condiciones")
        @AssertTrue(message = "Debe aceptar los términos y condiciones")
        Boolean aceptaTerminos,

        @NotNull(message = "Debe aceptar la política de privacidad")
        @AssertTrue(message = "Debe aceptar la política de privacidad")
        Boolean aceptaPrivacidad,

        // Opcional: información comercial. No condiciona el servicio.
        Boolean aceptaMarketing,

        // Versión de los textos que el usuario vio al aceptar
        @Size(max = 20)
        String versionLegal
) {}
