package com.polygraph.erp.modules.evaluados.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record EducacionRequest(
        String nivel,

        @NotBlank(message = "El nombre de la institución es obligatorio")
        String institucion,

        String titulo,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Boolean enCurso,
        String ciudad
) {}
