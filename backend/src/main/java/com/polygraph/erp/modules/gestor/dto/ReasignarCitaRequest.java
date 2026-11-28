package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Reasignar una cita que no se realizó a OTRO profesional, con su día y hora y el motivo de la lista de reprogramación. */
public record ReasignarCitaRequest(
        @NotNull(message = "Elige al nuevo profesional.") Long idUsuarioAsignado,
        @NotNull(message = "Indica la fecha y hora.") LocalDateTime fechaProgramada,
        @NotNull(message = "Elige el motivo.") Integer idMotivo,
        String texto,
        String lugar) {}
