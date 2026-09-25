package com.polygraph.erp.modules.clientes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El ADMIN_CLIENTE edita uno de sus propios usuarios ANALISTA_CLIENTE — a diferencia de
 *  ActualizarUsuarioClienteRequest (usado por un admin interno), acá no se puede cambiar el rol. */
public record ActualizarUsuarioPropioRequest(
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 100) String apellido,
        @NotBlank @Email @Size(max = 150) String email,
        // Opcional: vacío = no cambia la contraseña
        @Size(min = 8, max = 50) String password
) {}
