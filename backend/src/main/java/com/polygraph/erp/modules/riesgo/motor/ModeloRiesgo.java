package com.polygraph.erp.modules.riesgo.motor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tipos de entrada y salida del motor de calificación de riesgo (docs/modelo-calificacion-riesgo.md).
 * Todo inmutable y sin dependencias de Spring ni JPA: el motor es un cálculo puro — recibe la
 * configuración y los subprocesos, devuelve el resultado. La Fase D arma estas entradas desde la
 * base de datos y guarda la salida.
 */
public final class ModeloRiesgo {

    private ModeloRiesgo() {}

    public enum NivelRiesgo { BAJO, MEDIO, ALTO }

    public enum TipoAjuste { NINGUNO, PISO, CRITICO }

    /** Estado de un subproceso para el motor (ver §4 del documento). */
    public enum EstadoSubproceso { REALIZADO, NO_REALIZADO, PENDIENTE }

    /** CALCULADO: hay nivel final. EN_PROCESO: algún subproceso sigue pendiente. SIN_INFORMACION:
     *  nada con peso quedó realizado. NO_CALIFICA: la configuración del proceso no es válida
     *  (pesos que no suman 100 o subprocesos sin peso). */
    public enum EstadoResultado { CALCULADO, EN_PROCESO, SIN_INFORMACION, NO_CALIFICA }

    /** Umbrales de la criticidad del cargo: BAJO ≤ limiteBajo < MEDIO ≤ limiteMedio < ALTO. */
    public record Umbrales(BigDecimal limiteBajo, BigDecimal limiteMedio, BigDecimal minimoMedio) {}

    /** Regla A: algún subproceso con bruto ≥ umbralAlto. Regla B: cantidadModerados o más con bruto > umbralModerado. */
    public record ParametrosPiso(BigDecimal umbralAlto, BigDecimal umbralModerado, int cantidadModerados) {}

    public record ConfiguracionCalculo(
            Umbrales umbrales,
            ParametrosPiso pisos,
            BigDecimal coberturaMinima,
            /** Identifica con qué configuración se calculó (se guarda con el resultado). */
            String version) {}

    /** Una respuesta calificable elegida (el "hallazgo"): puntos y si es crítica. */
    public record Hallazgo(String descripcion, BigDecimal puntos, boolean critico) {}

    /** Un subproceso del caso. peso null = sin configurar (el proceso no califica); 0 = no
     *  participa en el promedio ni en los pisos, pero sus respuestas críticas SÍ cuentan. */
    public record ServicioEvaluado(
            String codigo,
            String nombre,
            BigDecimal peso,
            boolean obligatorio,
            EstadoSubproceso estado,
            List<Hallazgo> hallazgos) {}

    /** Resultado de un subproceso. Los puntajes van null si no quedó REALIZADO. */
    public record ResultadoServicio(
            String codigo,
            String nombre,
            EstadoSubproceso estado,
            BigDecimal peso,
            BigDecimal pesoEfectivo,
            BigDecimal puntajeBruto,
            BigDecimal puntajeFinal,
            NivelRiesgo nivel,
            List<Hallazgo> criticos) {

        public boolean tieneCritico() {
            return criticos != null && !criticos.isEmpty();
        }
    }

    /** Por qué se ajustó el resultado: servicio afectado, regla ("CRITICO", "REGLA_A", "REGLA_B") y detalle. */
    public record MotivoAjuste(String servicio, String regla, String detalle) {}

    /** Salida completa del motor — lo que va al cliente es solo nivelFinal y puntajeFinal; el
     *  resto es registro interno de auditoría. */
    public record ResultadoCalificacion(
            EstadoResultado estado,
            List<ResultadoServicio> servicios,
            BigDecimal puntajePonderado,
            NivelRiesgo nivelPorPuntaje,
            BigDecimal puntajeFinal,
            NivelRiesgo nivelFinal,
            TipoAjuste tipoAjuste,
            List<MotivoAjuste> motivosAjuste,
            BigDecimal cobertura,
            boolean requiereRevisionManual,
            List<String> motivosRevision,
            String version) {}
}
