package com.polygraph.erp.modules.riesgo.motor;

import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.EstadoSubproceso;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.Hallazgo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.ResultadoServicio;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.ServicioEvaluado;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.Umbrales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Paso 1 – Calificación por subproceso (§5):
 * puntaje_bruto = suma de los puntos de sus respuestas calificables, con tope en 100;
 * puntaje_final = 100 si tiene alguna respuesta crítica, si no = bruto; nivel con los umbrales.
 * Solo se califica lo REALIZADO; lo demás queda con puntajes en null.
 */
public final class CalificadorServicio {

    static final BigDecimal CIEN = new BigDecimal("100");
    static final int ESCALA = 4;

    private CalificadorServicio() {}

    public static ResultadoServicio calificar(ServicioEvaluado s, Umbrales umbrales) {
        if (s.estado() != EstadoSubproceso.REALIZADO) {
            return new ResultadoServicio(s.codigo(), s.nombre(), s.estado(), s.peso(), null, null, null, null, List.of());
        }
        List<Hallazgo> hallazgos = s.hallazgos() == null ? List.of() : s.hallazgos();
        BigDecimal suma = hallazgos.stream()
                .map(h -> h.puntos() == null ? BigDecimal.ZERO : h.puntos())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal bruto = suma.min(CIEN).setScale(ESCALA, RoundingMode.HALF_UP);
        List<Hallazgo> criticos = hallazgos.stream().filter(Hallazgo::critico).toList();
        BigDecimal fin = criticos.isEmpty() ? bruto : CIEN.setScale(ESCALA, RoundingMode.HALF_UP);
        return new ResultadoServicio(s.codigo(), s.nombre(), s.estado(), s.peso(), null,
                bruto, ClasificadorNivel.redondear(fin), ClasificadorNivel.clasificar(fin, umbrales), criticos);
    }
}
