package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.NotNull;

/** Cancelar un subproceso que no ha iniciado. El motivo sale de la lista de cancelación (admin); «Otro» exige texto. */
public record CancelarSubprocesoRequest(
        @NotNull(message = "Elige el motivo de la cancelación.") Integer idMotivo,
        String texto) {}
