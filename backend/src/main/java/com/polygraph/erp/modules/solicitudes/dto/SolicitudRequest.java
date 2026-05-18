package com.polygraph.erp.modules.solicitudes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SolicitudRequest(
        @NotBlank(message = "La cédula es obligatoria")
        String cedula,

        @NotBlank(message = "Los nombres son obligatorios")
        String nombres,

        @NotBlank(message = "Los apellidos son obligatorios")
        String apellidos,

        String celular,
        String email,
        String ciudad,
        String cargo,

        @NotEmpty(message = "Debe seleccionar al menos un servicio")
        List<Integer> serviciosIds,

        String notas
) {}
