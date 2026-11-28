package com.polygraph.erp.modules.riesgo.motor;

import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.ConfiguracionCalculo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.EstadoResultado;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.EstadoSubproceso;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.MotivoAjuste;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.ResultadoCalificacion;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.ResultadoServicio;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.ServicioEvaluado;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.TipoAjuste;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor de calificación de riesgo del evaluado — orquesta los 4 pasos de
 * docs/modelo-calificacion-riesgo.md §5 en orden. Cálculo puro: sin estado, sin base de datos.
 * El resultado es una RECOMENDACIÓN; la decisión final la toma el gestor (§6, Fase D).
 *
 * Antes del paso 1 se valida la configuración: si algún subproceso no tiene peso o los pesos no
 * suman 100 → NO_CALIFICA. Si algún subproceso sigue PENDIENTE → EN_PROCESO (sin nivel final).
 * En ambos casos igual se entrega el resultado de cada subproceso ya realizado.
 */
public final class MotorCalificacionRiesgo {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private MotorCalificacionRiesgo() {}

    public static ResultadoCalificacion calcular(ConfiguracionCalculo config, List<ServicioEvaluado> servicios) {
        // Paso 1 — cada subproceso por separado
        List<ResultadoServicio> porServicio = servicios.stream()
                .map(s -> CalificadorServicio.calificar(s, config.umbrales())).toList();

        String problemaConfig = validarPesos(servicios);
        if (problemaConfig != null) {
            return parcial(EstadoResultado.NO_CALIFICA, config, porServicio, List.of(problemaConfig));
        }
        if (servicios.stream().anyMatch(s -> s.estado() == EstadoSubproceso.PENDIENTE)) {
            return parcial(EstadoResultado.EN_PROCESO, config, porServicio, List.of());
        }

        // Paso 2 — críticos
        List<MotivoAjuste> criticos = DetectorCriticos.detectar(porServicio);

        // Paso 3 — promedio ponderado
        CalculadorPonderado.Resultado ponderado = CalculadorPonderado.calcular(porServicio);
        if (!ponderado.hayParticipantes() && criticos.isEmpty()) {
            return parcial(EstadoResultado.SIN_INFORMACION, config, ponderado.servicios(),
                    List.of("Ningún subproceso con peso quedó realizado"));
        }
        NivelRiesgo nivelPorPuntaje = ClasificadorNivel.clasificar(ponderado.puntajePonderado(), config.umbrales());

        // Paso 4 — crítico, pisos o sin ajuste
        BigDecimal puntajeFinal;
        NivelRiesgo nivelFinal;
        TipoAjuste ajuste;
        List<MotivoAjuste> motivos;
        if (!criticos.isEmpty()) {
            puntajeFinal = ClasificadorNivel.redondear(CIEN);
            nivelFinal = NivelRiesgo.ALTO;
            ajuste = TipoAjuste.CRITICO;
            motivos = criticos;
        } else {
            EvaluadorPisos.Resultado piso = EvaluadorPisos.evaluar(ponderado.servicios(), ponderado.puntajePonderado(),
                    nivelPorPuntaje, config.pisos(), config.umbrales().minimoMedio());
            if (piso.aplica()) {
                puntajeFinal = piso.puntajeFinal();
                nivelFinal = NivelRiesgo.MEDIO;
                ajuste = TipoAjuste.PISO;
                motivos = piso.motivos();
            } else {
                puntajeFinal = ponderado.puntajePonderado();
                nivelFinal = nivelPorPuntaje;
                ajuste = TipoAjuste.NINGUNO;
                motivos = List.of();
            }
        }

        List<String> revision = EvaluadorRevisionManual.motivos(nivelFinal, servicios, ponderado.cobertura(), config.coberturaMinima());
        return new ResultadoCalificacion(EstadoResultado.CALCULADO, ponderado.servicios(), ponderado.puntajePonderado(),
                nivelPorPuntaje, puntajeFinal, nivelFinal, ajuste, motivos, ponderado.cobertura(),
                !revision.isEmpty(), revision, config.version());
    }

    /** null si la configuración es válida; si no, el motivo. */
    private static String validarPesos(List<ServicioEvaluado> servicios) {
        if (servicios.isEmpty()) return "El proceso no tiene subprocesos";
        long sinPeso = servicios.stream().filter(s -> s.peso() == null).count();
        if (sinPeso > 0) return sinPeso == 1 ? "1 subproceso sin peso" : sinPeso + " subprocesos sin peso";
        BigDecimal suma = servicios.stream().map(ServicioEvaluado::peso).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (suma.compareTo(CIEN) != 0) {
            return "Los pesos suman " + EvaluadorPisos.texto(suma) + " % (deben sumar 100 %)";
        }
        return null;
    }

    /** Resultado sin nivel final: se entregan los subprocesos calculados y el motivo. Siempre
     *  requiere revisión manual salvo EN_PROCESO (todavía no hay nada que revisar). */
    private static ResultadoCalificacion parcial(EstadoResultado estado, ConfiguracionCalculo config,
                                                 List<ResultadoServicio> servicios, List<String> motivos) {
        return new ResultadoCalificacion(estado, servicios, null, null, null, null, null, List.of(), null,
                estado != EstadoResultado.EN_PROCESO, new ArrayList<>(motivos), config.version());
    }
}
