package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DepartamentoRequest(
        @NotBlank(message = "El nombre del departamento es obligatorio")
        @Size(max = 100)
        String nombre,

        @NotBlank(message = "El código DANE es obligatorio")
        @Pattern(regexp = "\\d{2}", message = "El código DANE del departamento debe ser numérico de 2 dígitos")
        String codigoDane
) {}
