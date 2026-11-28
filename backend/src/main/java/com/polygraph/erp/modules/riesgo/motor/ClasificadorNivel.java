package com.polygraph.erp.modules.riesgo.motor;

import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.Umbrales;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Convierte un puntaje en nivel con los umbrales de la criticidad del cargo. El puntaje se
 *  redondea a 1 decimal (HALF_UP) ANTES de clasificar, para que el número mostrado y el nivel
 *  nunca se contradigan (§5, Redondeo). */
public final class ClasificadorNivel {

    private ClasificadorNivel() {}

    public static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(1, RoundingMode.HALF_UP);
    }

    public static NivelRiesgo clasificar(BigDecimal puntaje, Umbrales umbrales) {
        BigDecimal p = redondear(puntaje);
        if (p.compareTo(umbrales.limiteBajo()) <= 0) return NivelRiesgo.BAJO;
        if (p.compareTo(umbrales.limiteMedio()) <= 0) return NivelRiesgo.MEDIO;
        return NivelRiesgo.ALTO;
    }
}
