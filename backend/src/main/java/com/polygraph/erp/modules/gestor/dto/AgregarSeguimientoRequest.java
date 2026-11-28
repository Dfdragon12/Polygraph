package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Una observación más sobre una novedad abierta (ej. "el candidato llamó, llega en 30 min"). */
public record AgregarSeguimientoRequest(
        @NotBlank(message = "Escribe la observación.")
        @Size(max = 2000, message = "La observación no puede superar 2000 caracteres.") String texto) {}
