package com.polygraph.erp.modules.clientes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lo único que el analista de un cliente puede cambiar de sí mismo. El correo y la empresa los
 *  administra el administrador de su empresa o Polygraph. */
public record ActualizarMiPerfilAnalistaClienteRequest(
        @NotBlank(message = "El nombre es requerido") @Size(max = 100) String nombre,
        @Size(max = 100) String apellido
) {}
