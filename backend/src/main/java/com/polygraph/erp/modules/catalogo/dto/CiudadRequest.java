package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CiudadRequest(
        @NotBlank(message = "El nombre de la ciudad es obligatorio")
        @Size(max = 150)
        String nombreCiudad,

        @NotBlank(message = "El departamento es obligatorio")
        @Size(max = 100)
        String departamento,

        @Pattern(regexp = "\\d{1,10}|", message = "El código DANE de la ciudad debe ser numérico")
        String codigoDaneCiudad,

        @Pattern(regexp = "\\d{1,2}|", message = "El código DANE del departamento debe ser numérico")
        String codigoDaneDepto
) {}
