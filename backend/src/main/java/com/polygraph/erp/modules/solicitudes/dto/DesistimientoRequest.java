package com.polygraph.erp.modules.solicitudes.dto;

import jakarta.validation.constraints.NotNull;

/** El candidato desistió. El motivo sale de la lista de cancelación («El candidato desistió» u otro); «Otro» exige texto. */
public record DesistimientoRequest(
        @NotNull(message = "Elige el motivo.") Integer idMotivo,
        String texto) {}
