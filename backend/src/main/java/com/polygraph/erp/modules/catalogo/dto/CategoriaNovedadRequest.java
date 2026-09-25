package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoriaNovedadRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres.") String nombre,
        @NotBlank(message = "Elige un color.")
        @Pattern(regexp = "red|orange|amber|green|sky|blue|violet|slate|gray", message = "Color no válido.") String color,
        Integer orden) {}
