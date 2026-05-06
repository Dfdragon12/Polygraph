package com.polygraph.erp.modules.auth.dto;

import jakarta.validation.constraints.*;

public record RegistroJuridicaRequest(
        @NotBlank(message = "El NIT es requerido")
        @Size(max = 20)
        String nit,

        @Size(max = 1)
        String dv,

        @NotBlank(message = "La razón social es requerida")
        @Size(max = 200)
        String razonSocial,

        @Size(max = 200)
        String nombreComercial,

        @Size(max = 150)
        String representanteLegal,

        @NotBlank(message = "El email es requerido")
        @Email(message = "Email inválido")
        @Size(max = 150)
        String email,

        @NotBlank(message = "La contraseña es requerida")
        @Size(min = 8, max = 50, message = "Mínimo 8 caracteres")
        String password,

        @Size(max = 20)
        String telefono,

        @NotBlank(message = "El tipo de cliente es requerido")
        String tipoCliente
) {}
