package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.NotBlank;

/** Gestor/programador devuelve un subproceso COMPLETADO a quien lo diligenció (vuelve a
 *  ASIGNADO) — la observación es obligatoria: sin ella, quien lo recibe de vuelta no sabe qué
 *  corregir. */
public record DevolverSubprocesoRequest(@NotBlank(message = "Explica qué hay que corregir.") String observacion) {}
