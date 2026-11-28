package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** La respuesta es obligatoria: es lo que le llega a quien reportó la novedad (qué se decidió).
 *  nuevaFechaProgramada es opcional — si viene, además de cerrar la novedad se corre la hora del
 *  subproceso a esa fecha (ver AsignacionService.reprogramar). */
public record ResolverNovedadRequest(
        @NotBlank(message = "Escribe qué se hizo con esta novedad.")
        @Size(max = 2000, message = "La respuesta no puede superar 2000 caracteres.") String respuesta,
        LocalDateTime nuevaFechaProgramada,
        /** true = además de cerrar la novedad, el subproceso queda como "No realizado" (no
         *  penaliza en el riesgo). Excluyente con nuevaFechaProgramada. */
        Boolean marcarNoRealizado) {

    /** Compatibilidad con quien resuelve sin reprogramar. */
    public ResolverNovedadRequest(String respuesta) {
        this(respuesta, null, null);
    }

    public ResolverNovedadRequest(String respuesta, LocalDateTime nuevaFechaProgramada) {
        this(respuesta, nuevaFechaProgramada, null);
    }
}
