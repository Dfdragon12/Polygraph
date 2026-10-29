package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record AsignarRequest(
        @NotNull Long idUsuarioAsignado,
        @NotNull LocalDateTime fechaProgramada,
        String observaciones,
        // Si viene null, se usa el estimado del catálogo (tipoProgreso.minutosEstimados).
        Integer duracionMinutos,
        // Dónde es la cita (poligrafía/visita): dirección o "Virtual". Lo ve el candidato en su
        // portal. Opcional: una visita sin lugar muestra la dirección registrada del candidato.
        @Size(max = 300, message = "El lugar puede tener hasta 300 caracteres") String lugar
) {}
