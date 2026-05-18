package com.polygraph.erp.modules.evaluados.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ExperienciaLaboralRequest(
        @NotBlank(message = "El nombre de la empresa es obligatorio")
        String empresa,

        @NotBlank(message = "El cargo es obligatorio")
        String cargo,

        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate fechaInicio,

        LocalDate fechaFin,
        Boolean laboraActualmente,
        String ciudad,
        String telefonoEmpresa,
        String motivoRetiro,
        String nombreJefe,
        String cargoJefe
) {}
