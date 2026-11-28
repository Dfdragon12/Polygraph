package com.polygraph.erp.modules.usuarios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Lo que un usuario interno puede cambiar de sí mismo. El correo, el rol, el documento y lo que
 *  cubre (ciudades, subprocesos) NO están aquí a propósito: los administra ADMIN_POLYGRAPH. */
public record ActualizarPerfilInternoRequest(
        @NotBlank(message = "El nombre es requerido") @Size(max = 100) String nombre,
        @Size(max = 100) String apellido,
        @Pattern(regexp = "^$|^[0-9+()\\s-]{7,20}$", message = "Teléfono inválido: usa solo números, espacios, +, - y paréntesis (7 a 20 caracteres)")
        String telefono,
        Integer idCiudadResidencia
) {}
