package com.polygraph.erp.modules.evaluados.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record EducacionRequest(
        @NotBlank(message = "El nivel educativo es obligatorio")
        String nivel,

        @NotBlank(message = "El nombre de la institución es obligatorio")
        String institucion,

        String titulo,

        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate fechaInicio,

        LocalDate fechaFin,
        Boolean enCurso,

        @NotBlank(message = "La ciudad es obligatoria")
        String ciudad
) {}
