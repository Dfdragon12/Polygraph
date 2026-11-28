package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.NotNull;

/** La cita no se realizó (el evaluado no se presentó, avisó que no asistirá...). El motivo sale de la lista de reprogramación; «Otro» exige texto. */
public record CitaNoRealizadaRequest(
        @NotNull(message = "Elige el motivo.") Integer idMotivo,
        String texto) {}
