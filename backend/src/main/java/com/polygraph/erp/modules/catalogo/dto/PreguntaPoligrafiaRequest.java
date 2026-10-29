package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PreguntaPoligrafiaRequest(
        @NotBlank(message = "El nombre del tema es obligatorio")
        @Size(max = 150, message = "El tema admite máximo 150 caracteres")
        String nombre,

        // El cliente debe escribir cuál (tema tipo "Otros ¿Cuál?")
        Boolean requiereDetalle,

        // null al crear = va al final de la lista
        Integer orden,

        Boolean activo
) {}
