package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DocumentoRequeridoRequest(
        @NotBlank(message = "El nombre del documento es obligatorio")
        @Size(max = 150)
        String nombre,

        // Identificador estable (minúsculas, guiones bajos) — es lo que el candidato manda como
        // tipoDocumento al subir el archivo. Si viene vacío, se deriva del nombre (ver
        // ProcesoService.claveDesdeNombre).
        @Pattern(regexp = "[a-z0-9_]*", message = "La clave solo puede tener minúsculas, números y guion bajo")
        @Size(max = 100)
        String clave,

        Boolean obligatorio,

        Integer orden
) {}
