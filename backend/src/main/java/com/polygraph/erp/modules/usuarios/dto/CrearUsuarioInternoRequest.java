package com.polygraph.erp.modules.usuarios.dto;

import com.polygraph.erp.shared.enums.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearUsuarioInternoRequest(
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 100) String apellido,
        @NotBlank @Email @Size(max = 150) String email,
        // Opcional: si viene vacío se usa el número de documento como contraseña
        @Size(min = 8, max = 50) String password,
        @NotNull Rol rol,
        @Size(max = 20) String telefono,
        @Size(max = 20) String tipoDocumento,
        @Size(max = 20) String documento,
        @Size(max = 100) String ciudadResidencia,
        @Size(max = 100) String salaEncargada,
        String novedadesSala,
        @Size(max = 200) String zonasVisita
) {}
