package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BarrioRequest(
        @NotBlank(message = "El nombre del barrio es obligatorio")
        @Size(max = 150)
        String nombre,

        @NotNull(message = "La ciudad es obligatoria")
        Integer idCiudad
) {}
