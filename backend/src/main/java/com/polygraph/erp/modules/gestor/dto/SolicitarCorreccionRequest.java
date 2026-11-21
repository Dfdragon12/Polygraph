package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.NotBlank;

/** PB-02: el ejecutor pide corregir un subproceso ya publicado; el motivo es obligatorio. */
public record SolicitarCorreccionRequest(@NotBlank(message = "Cuenta qué hay que corregir.") String motivo) {}
