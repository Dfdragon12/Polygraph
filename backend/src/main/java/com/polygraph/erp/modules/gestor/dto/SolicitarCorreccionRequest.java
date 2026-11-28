package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * PB-02 / PC-04: el ejecutor pide corregir un subproceso ya publicado. «Qué hay que corregir» (tipo) y «Explícale al gestor» (motivo) son obligatorios.
 * tipo: DATO_ERRADO, SOPORTE_EQUIVOCADO, HALLAZGO_MAL_CALIFICADO u OTRO.
 */
public record SolicitarCorreccionRequest(
        @NotBlank(message = "Elige qué hay que corregir.") String tipo,
        @NotBlank(message = "Explícale al gestor qué hay que corregir.") String motivo) {}
