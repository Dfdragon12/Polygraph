package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** idTipoNovedad: una opción del catálogo tipos_novedad (activa y aplicable al subproceso). La descripción es obligatoria incluso con un tipo ya
 *  claro ("el candidato no llegó") — quien coordina necesita el contexto (a qué hora, si se
 *  intentó llamar, etc.) para decidir si reprograma o cancela. */
public record RegistrarNovedadRequest(
        @NotNull(message = "Selecciona el tipo de novedad.") Integer idTipoNovedad,
        @NotBlank(message = "Describe qué pasó.")
        @Size(max = 2000, message = "La descripción no puede superar 2000 caracteres.") String descripcion) {}
