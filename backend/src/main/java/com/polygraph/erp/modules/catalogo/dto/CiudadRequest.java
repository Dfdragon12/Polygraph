package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CiudadRequest(
        @NotBlank(message = "El nombre de la ciudad es obligatorio")
        @Size(max = 150)
        String nombreCiudad,

        @NotBlank(message = "El departamento es obligatorio")
        @Size(max = 100)
        String departamento,

        @Size(max = 10)
        String codigoDaneCiudad,

        @Size(max = 2)
        String codigoDaneDepto
) {}
