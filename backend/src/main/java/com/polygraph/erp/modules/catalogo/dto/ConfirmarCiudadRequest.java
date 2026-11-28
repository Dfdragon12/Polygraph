package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Opcional: si la ciudad todavía no tiene código DANE (se creó desde el formulario público de un
 * cliente/candidato sin sesión, que no lo conoce), el admin debe completarlo aquí antes de poder
 * confirmarla — ver CiudadService.confirmar. Si la ciudad ya tenía ambos códigos, este cuerpo
 * puede venir vacío/null y no cambia nada.
 */
public record ConfirmarCiudadRequest(
        @Pattern(regexp = "\\d{1,10}|", message = "El código DANE de la ciudad debe ser numérico")
        String codigoDaneCiudad,

        @Pattern(regexp = "\\d{1,2}|", message = "El código DANE del departamento debe ser numérico")
        String codigoDaneDepto
) {}
