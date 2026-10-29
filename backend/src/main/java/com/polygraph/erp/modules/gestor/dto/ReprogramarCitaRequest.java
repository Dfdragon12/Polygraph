package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Reprogramar una cita que no se realizó (mismo profesional, nueva fecha y hora), con motivo de la lista de reprogramación. */
public record ReprogramarCitaRequest(
        @NotNull(message = "Indica la nueva fecha y hora.") LocalDateTime fechaProgramada,
        @NotNull(message = "Elige el motivo.") Integer idMotivo,
        String texto,
        // null = se conserva el lugar de la cita
        String lugar) {}
