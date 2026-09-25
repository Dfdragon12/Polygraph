package com.polygraph.erp.modules.riesgo.motor;

import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.EstadoSubproceso;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.ServicioEvaluado;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Revisión manual (§5, paso 4.5): nivel final MEDIO o ALTO; un obligatorio NO_REALIZADO; o
 *  cobertura menor a la mínima del proceso. Devuelve los motivos (vacío = no requiere). */
public final class EvaluadorRevisionManual {

    private EvaluadorRevisionManual() {}

    public static List<String> motivos(NivelRiesgo nivelFinal, List<ServicioEvaluado> servicios,
                                       BigDecimal cobertura, BigDecimal coberturaMinima) {
        List<String> motivos = new ArrayList<>();
        if (nivelFinal == NivelRiesgo.MEDIO || nivelFinal == NivelRiesgo.ALTO) {
            motivos.add("Nivel " + nivelFinal);
        }
        servicios.stream()
                .filter(s -> s.obligatorio() && s.estado() == EstadoSubproceso.NO_REALIZADO)
                .forEach(s -> motivos.add("Subproceso obligatorio no realizado: " + s.nombre()));
        if (cobertura.compareTo(coberturaMinima) < 0) {
            motivos.add("Cobertura " + EvaluadorPisos.texto(cobertura) + " % menor a la mínima "
                    + EvaluadorPisos.texto(coberturaMinima) + " %");
        }
        return motivos;
    }
}
