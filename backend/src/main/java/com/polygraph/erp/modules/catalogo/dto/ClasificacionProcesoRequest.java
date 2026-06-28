package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClasificacionProcesoRequest(
        @NotBlank @Size(max = 5)   String codigo,
        @NotBlank @Size(max = 100) String nombre,
        String descripcion
) {}
