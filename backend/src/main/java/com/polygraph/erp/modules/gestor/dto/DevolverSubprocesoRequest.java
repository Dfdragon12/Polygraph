package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.NotNull;

/** Gestor/programador devuelve un subproceso FINALIZADO a quien lo diligenció (vuelve a EN_EJECUCION con la marca
 *  DEVUELTO). El motivo sale de la lista de devolución (admin); «Otro» exige explicar cuál. La observación aclara qué corregir. */
public record DevolverSubprocesoRequest(
        @NotNull(message = "Elige el motivo de la devolución.") Integer idMotivo,
        String observacion) {}
