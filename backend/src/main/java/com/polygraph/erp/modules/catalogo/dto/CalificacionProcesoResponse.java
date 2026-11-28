package com.polygraph.erp.modules.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;

/** Configuración de calificación de riesgo de un proceso: pesos por subproceso, obligatorios,
 *  cobertura mínima y criticidad por defecto. califica = true solo si los pesos de los
 *  subprocesos que cuentan suman exactamente 100 y ninguno está sin configurar. */
public record CalificacionProcesoResponse(
        Integer idProceso,
        String nombreProceso,
        String criticidadDefecto,
        BigDecimal coberturaMinima,
        Integer versionCalificacion,
        BigDecimal sumaPesos,
        boolean califica,
        String motivoNoCalifica,
        List<SubprocesoPeso> subprocesos
) {
    /** cuenta: habilitado en el proceso y activo en el catálogo — solo estos suman. */
    public record SubprocesoPeso(
            Integer idPaso,
            Integer idTipoProgreso,
            String nombreProgreso,
            BigDecimal peso,
            boolean obligatorio,
            boolean habilitado,
            boolean tipoActivo,
            boolean cuenta) {}
}
