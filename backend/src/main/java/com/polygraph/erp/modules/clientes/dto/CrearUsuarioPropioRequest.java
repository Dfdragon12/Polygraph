package com.polygraph.erp.modules.clientes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El ADMIN_CLIENTE crea sus propios usuarios ANALISTA_CLIENTE (autoservicio, tope de
 *  ClienteAdminService.MAX_ANALISTAS_CLIENTE) — a diferencia de CrearUsuarioClienteRequest (usado
 *  por un admin interno), acá no se elige el rol: siempre es ANALISTA_CLIENTE. */
public record CrearUsuarioPropioRequest(
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 100) String apellido,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 8, max = 50) String password
) {}
