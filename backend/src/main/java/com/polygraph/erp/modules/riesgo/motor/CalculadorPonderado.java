package com.polygraph.erp.modules.riesgo.motor;

import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.EstadoSubproceso;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.ResultadoServicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Paso 3 – Promedio ponderado (§5). Participan solo los subprocesos REALIZADOS con peso > 0. El
 * peso de los NO_REALIZADOS se reparte proporcionalmente: peso_efectivo = peso / Σ pesos
 * realizados × 100. puntaje_ponderado = Σ (bruto × peso) / Σ pesos realizados — algebraicamente
 * igual a Σ bruto × peso_efectivo / 100, pero sin arrastrar el redondeo del peso efectivo (caso
 * 9: 60 × 25 / 75 = 20 exacto). Usa el puntaje BRUTO (sin el 100 por crítico).
 */
public final class CalculadorPonderado {

    public record Resultado(List<ResultadoServicio> servicios, BigDecimal puntajePonderado, BigDecimal cobertura,
                            boolean hayParticipantes) {}

    private CalculadorPonderado() {}

    static boolean participa(ResultadoServicio s) {
        return s.estado() == EstadoSubproceso.REALIZADO && s.peso() != null && s.peso().signum() > 0;
    }

    public static Resultado calcular(List<ResultadoServicio> servicios) {
        BigDecimal sumaPesos = servicios.stream().filter(CalculadorPonderado::participa)
                .map(ResultadoServicio::peso).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sumaPesos.signum() == 0) {
            return new Resultado(servicios, BigDecimal.ZERO.setScale(1), BigDecimal.ZERO.setScale(2), false);
        }
        BigDecimal numerador = BigDecimal.ZERO;
        List<ResultadoServicio> conPeso = servicios.stream().map(s -> {
            if (!participa(s)) {
                return s.estado() == EstadoSubproceso.REALIZADO ? conPesoEfectivo(s, BigDecimal.ZERO) : s;
            }
            BigDecimal efectivo = s.peso().multiply(CalificadorServicio.CIEN)
                    .divide(sumaPesos, CalificadorServicio.ESCALA, RoundingMode.HALF_UP);
            return conPesoEfectivo(s, efectivo);
        }).toList();
        for (ResultadoServicio s : servicios) {
            if (participa(s)) numerador = numerador.add(s.puntajeBruto().multiply(s.peso()));
        }
        BigDecimal ponderado = ClasificadorNivel.redondear(
                numerador.divide(sumaPesos, CalificadorServicio.ESCALA, RoundingMode.HALF_UP));
        // Cobertura relativa a la configuración del propio proceso (que suma 100).
        return new Resultado(conPeso, ponderado, sumaPesos.setScale(2, RoundingMode.HALF_UP), true);
    }

    private static ResultadoServicio conPesoEfectivo(ResultadoServicio s, BigDecimal efectivo) {
        return new ResultadoServicio(s.codigo(), s.nombre(), s.estado(), s.peso(), efectivo,
                s.puntajeBruto(), s.puntajeFinal(), s.nivel(), s.criticos());
    }
}
