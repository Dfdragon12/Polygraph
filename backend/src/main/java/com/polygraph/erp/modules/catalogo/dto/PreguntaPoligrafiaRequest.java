package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PreguntaPoligrafiaRequest(
        @NotBlank(message = "El nombre del tema es obligatorio")
        @Size(max = 150, message = "El tema admite máximo 150 caracteres")
        String nombre,

        // El cliente debe escribir cuál (tema tipo "Otros ¿Cuál?")
        Boolean requiereDetalle,
        // Grupo del tema (opcional, máximo 100 caracteres)
        @Size(max = 100, message = "El grupo admite máximo 100 caracteres")
        String grupo,

        // null al crear = va al final de la lista
        Integer orden,

        Boolean activo
) {}
