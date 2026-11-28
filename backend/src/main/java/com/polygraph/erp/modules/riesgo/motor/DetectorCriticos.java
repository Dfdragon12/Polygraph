package com.polygraph.erp.modules.riesgo.motor;

import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.MotivoAjuste;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.ResultadoServicio;

import java.util.List;

/** Paso 2 – Hallazgos críticos (knock-out, §5): cualquier respuesta crítica de un subproceso
 *  REALIZADO lleva el estudio a ALTO. Incluye subprocesos con peso 0 (decisión 25-sep-2026: lo
 *  crítico siempre cuenta; el peso 0 solo lo saca del promedio y de los pisos). */
public final class DetectorCriticos {

    private DetectorCriticos() {}

    public static List<MotivoAjuste> detectar(List<ResultadoServicio> servicios) {
        return servicios.stream()
                .filter(ResultadoServicio::tieneCritico)
                .flatMap(s -> s.criticos().stream()
                        .map(h -> new MotivoAjuste(s.nombre(), "CRITICO", h.descripcion())))
                .toList();
    }
}
