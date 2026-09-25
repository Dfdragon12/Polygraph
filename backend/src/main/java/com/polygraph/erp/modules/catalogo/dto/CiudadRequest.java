package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CiudadRequest(
        @NotBlank(message = "El nombre de la ciudad es obligatorio")
        @Size(max = 150)
        String nombreCiudad,

        // Debe ser uno de los departamentos del catálogo (ver DepartamentoService) — se valida en
        // CiudadService.crear(), no acá, porque necesita la lista para dar el mensaje de error con
        // el nombre que escribió el usuario.
        @NotBlank(message = "El departamento es obligatorio")
        @Size(max = 100)
        String departamento,

        @Pattern(regexp = "\\d{1,10}|", message = "El código DANE de la ciudad debe ser numérico")
        String codigoDaneCiudad
        // codigoDaneDepto ya no se recibe acá — se deriva siempre del departamento (ver
        // DepartamentoService), para que nunca quede desalineado con lo que se eligió.
) {}
