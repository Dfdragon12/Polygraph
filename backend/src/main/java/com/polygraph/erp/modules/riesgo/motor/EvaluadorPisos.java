package com.polygraph.erp.modules.riesgo.motor;

import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.MotivoAjuste;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.ParametrosPiso;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.ResultadoServicio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Paso 4 – Reglas de piso (§5). Solo cuentan subprocesos REALIZADOS con peso > 0 (los de peso 0
 * no califican). Regla A: alguno con bruto ≥ umbralAlto. Regla B: cantidadModerados o más con
 * bruto > umbralModerado. Si se cumple alguna y el nivel por puntaje es BAJO, sube a MEDIO con
 * puntaje = max(ponderado, mínimo MEDIO). Nunca sube a ALTO; con MEDIO o ALTO no cambia nada.
 */
public final class EvaluadorPisos {

    public record Resultado(boolean aplica, BigDecimal puntajeFinal, List<MotivoAjuste> motivos) {}

    private EvaluadorPisos() {}

    public static Resultado evaluar(List<ResultadoServicio> servicios, BigDecimal ponderado, NivelRiesgo nivelPorPuntaje,
                                    ParametrosPiso pisos, BigDecimal minimoMedio) {
        List<ResultadoServicio> cuentan = servicios.stream().filter(CalculadorPonderado::participa).toList();
        List<MotivoAjuste> motivos = new ArrayList<>();

        cuentan.stream()
                .filter(s -> s.puntajeBruto().compareTo(pisos.umbralAlto()) >= 0)
                .forEach(s -> motivos.add(new MotivoAjuste(s.nombre(), "REGLA_A",
                        "Puntaje " + texto(s.puntajeBruto()) + " ≥ " + texto(pisos.umbralAlto()))));

        List<ResultadoServicio> moderados = cuentan.stream()
                .filter(s -> s.puntajeBruto().compareTo(pisos.umbralModerado()) > 0).toList();
        if (moderados.size() >= pisos.cantidadModerados()) {
            moderados.forEach(s -> motivos.add(new MotivoAjuste(s.nombre(), "REGLA_B",
                    "Puntaje " + texto(s.puntajeBruto()) + " > " + texto(pisos.umbralModerado())
                            + " (" + moderados.size() + " subprocesos moderados)")));
        }

        if (motivos.isEmpty() || nivelPorPuntaje != NivelRiesgo.BAJO) {
            return new Resultado(false, ponderado, List.of());
        }
        return new Resultado(true, ClasificadorNivel.redondear(ponderado.max(minimoMedio)), motivos);
    }

    static String texto(BigDecimal v) {
        return ClasificadorNivel.redondear(v).toPlainString().replace('.', ',');
    }
}
