package com.polygraph.erp.modules.usuarios.dto;

import jakarta.validation.constraints.NotNull;

// Una ciudad asignada a un empleado. Para POLIGRAFISTA lleva numeroSala; para VISITADOR
// numeroSala queda null (solo cubre la ciudad).
public record CiudadAsignadaRequest(
        @NotNull(message = "La ciudad es obligatoria") Integer idCiudad,
        Integer numeroSala
) {}
