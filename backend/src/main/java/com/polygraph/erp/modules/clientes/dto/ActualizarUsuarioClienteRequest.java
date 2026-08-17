package com.polygraph.erp.modules.clientes.dto;

import com.polygraph.erp.shared.enums.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ActualizarUsuarioClienteRequest(
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 100) String apellido,
        @NotBlank @Email @Size(max = 150) String email,
        // Opcional: vacío = no cambia la contraseña
        @Size(min = 8, max = 50) String password,
        @NotNull Rol rol
) {}
