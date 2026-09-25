package com.polygraph.erp.modules.riesgo.motor;

import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de aceptación del modelo (prompts-cursor-motor-riesgo.md, Prompt 5): mismos casos y
 * mismos números. Si alguno no da, NO se ajusta la prueba: se revisa el motor contra
 * docs/modelo-calificacion-riesgo.md.
 * Estudio AVANZADO de la especificación: Antecedentes 25 · Validaciones 20 · Financiero 15 ·
 * Visita 15 · Poligrafía 25; obligatorios Antecedentes y Poligrafía; cobertura mínima 60 %.
 */
@DisplayName("Motor de riesgo — pruebas de aceptación de la especificación")
class MotorCalificacionRiesgoAceptacionTest {

    static final Umbrales ESTANDAR = new Umbrales(bd("30"), bd("60"), bd("31"));
    static final Umbrales SENSIBLE = new Umbrales(bd("25"), bd("50"), bd("26"));
    static final Umbrales CRITICO = new Umbrales(bd("20"), bd("40"), bd("21"));
    static final ParametrosPiso PISOS = new ParametrosPiso(bd("70"), bd("50"), 2);

    static final Object NR = "N/R";      // subproceso NO_REALIZADO
    static final Object CRIT0 = "CRIT0"; // crítico con 0 puntos (caso 8)

    /** Pesos del estudio AVANZADO, en el orden de la tabla: Ant, Val, Fin, Vis, Pol. */
    static final String[] NOMBRES = {"Antecedentes", "Validaciones", "Financiero", "Visita", "Poligrafía"};
    static final String[] PESOS = {"25", "20", "15", "15", "25"};

    static Stream<Arguments> casos() {
        return Stream.of(
                //         #  cargo     Ant  Val    Fin  Vis  Pol   Pond    Final   Nivel               Ajuste               Revisión
                Arguments.of(1, ESTANDAR, 0, 0, 0, 0, 0, "0.0", "0.0", NivelRiesgo.BAJO, TipoAjuste.NINGUNO, false),
                Arguments.of(2, ESTANDAR, 0, 0, 100, 0, 0, "15.0", "31.0", NivelRiesgo.MEDIO, TipoAjuste.PISO, true),
                Arguments.of(3, CRITICO, 0, 0, 100, 0, 0, "15.0", "21.0", NivelRiesgo.MEDIO, TipoAjuste.PISO, true),
                Arguments.of(4, ESTANDAR, 0, 55, 0, 55, 0, "19.3", "31.0", NivelRiesgo.MEDIO, TipoAjuste.PISO, true),
                Arguments.of(5, ESTANDAR, 40, 31, 0, 0, 20, "21.2", "21.2", NivelRiesgo.BAJO, TipoAjuste.NINGUNO, false),
                Arguments.of(6, CRITICO, 40, 31, 0, 0, 20, "21.2", "21.2", NivelRiesgo.MEDIO, TipoAjuste.NINGUNO, true),
                Arguments.of(7, ESTANDAR, 100, 0, 0, 0, 80, "45.0", "45.0", NivelRiesgo.MEDIO, TipoAjuste.NINGUNO, true),
                Arguments.of(8, ESTANDAR, 0, CRIT0, 0, 0, 0, "0.0", "100.0", NivelRiesgo.ALTO, TipoAjuste.CRITICO, true),
                Arguments.of(9, ESTANDAR, 60, 0, 0, 0, NR, "20.0", "20.0", NivelRiesgo.BAJO, TipoAjuste.NINGUNO, true)
        );
    }

    @ParameterizedTest(name = "Caso {0}")
    @MethodSource("casos")
    void estudioAvanzado(int caso, Umbrales umbrales, Object ant, Object val, Object fin, Object vis, Object pol,
                         String ponderado, String fin_, NivelRiesgo nivel, TipoAjuste ajuste, boolean revision) {
        Object[] brutos = {ant, val, fin, vis, pol};
        List<ServicioEvaluado> servicios = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            servicios.add(servicio(NOMBRES[i], PESOS[i], i == 0 || i == 4, brutos[i]));
        }
        ResultadoCalificacion r = MotorCalificacionRiesgo.calcular(config(umbrales, "60"), servicios);

        assertThat(r.estado()).isEqualTo(EstadoResultado.CALCULADO);
        assertThat(r.puntajePonderado()).isEqualByComparingTo(ponderado);
        assertThat(r.puntajeFinal()).isEqualByComparingTo(fin_);
        assertThat(r.nivelFinal()).isEqualTo(nivel);
        assertThat(r.tipoAjuste()).isEqualTo(ajuste);
        assertThat(r.requiereRevisionManual()).isEqualTo(revision);

        // Motivos de ajuste y de revisión, caso por caso
        switch (caso) {
            case 1, 5 -> {
                assertThat(r.motivosAjuste()).isEmpty();
                assertThat(r.motivosRevision()).isEmpty();
            }
            case 2, 3 -> {
                assertThat(r.motivosAjuste()).extracting(MotivoAjuste::servicio, MotivoAjuste::regla)
                        .containsExactly(org.assertj.core.groups.Tuple.tuple("Financiero", "REGLA_A"));
                assertThat(r.motivosRevision()).containsExactly("Nivel MEDIO");
            }
            case 4 -> assertThat(r.motivosAjuste()).extracting(MotivoAjuste::servicio, MotivoAjuste::regla)
                    .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("Validaciones", "REGLA_B"),
                            org.assertj.core.groups.Tuple.tuple("Visita", "REGLA_B"));
            case 6, 7 -> {
                assertThat(r.motivosAjuste()).isEmpty(); // caso 7: el piso no aplica porque ya es MEDIO
                assertThat(r.motivosRevision()).containsExactly("Nivel MEDIO");
            }
            case 8 -> {
                assertThat(r.motivosAjuste()).extracting(MotivoAjuste::servicio, MotivoAjuste::regla)
                        .containsExactly(org.assertj.core.groups.Tuple.tuple("Validaciones", "CRITICO"));
                ResultadoServicio val8 = r.servicios().get(1);
                assertThat(val8.puntajeBruto()).isEqualByComparingTo("0");
                assertThat(val8.puntajeFinal()).isEqualByComparingTo("100"); // crítico con 0 puntos → 100
                assertThat(r.motivosRevision()).containsExactly("Nivel ALTO");
            }
            case 9 -> {
                assertThat(r.motivosRevision()).containsExactly("Subproceso obligatorio no realizado: Poligrafía");
                assertThat(r.servicios().subList(0, 4)).extracting(ResultadoServicio::pesoEfectivo)
                        .usingElementComparator(BigDecimal::compareTo)
                        .containsExactly(bd("33.3333"), bd("26.6667"), bd("20"), bd("20"));
                assertThat(r.servicios().get(4).pesoEfectivo()).isNull(); // Poligrafía no realizada: no participa
                assertThat(r.cobertura()).isEqualByComparingTo("75");
            }
            default -> { }
        }
    }

    @Test
    @DisplayName("Caso 10 — Individual FINANCIERO, cargo estándar: 40 → MEDIO, revisión")
    void caso10_individualFinanciero() {
        ResultadoCalificacion r = MotorCalificacionRiesgo.calcular(config(ESTANDAR, "100"),
                List.of(servicio("Financiero", "100", true, 40)));
        assertThat(r.puntajePonderado()).isEqualByComparingTo("40.0");
        assertThat(r.puntajeFinal()).isEqualByComparingTo("40.0");
        assertThat(r.nivelFinal()).isEqualTo(NivelRiesgo.MEDIO);
        assertThat(r.tipoAjuste()).isEqualTo(TipoAjuste.NINGUNO);
        assertThat(r.requiereRevisionManual()).isTrue();
    }

    @Test
    @DisplayName("Caso 11 — Individual ANTECEDENTES, cargo sensible: 20 → BAJO, sin revisión")
    void caso11_individualAntecedentes() {
        ResultadoCalificacion r = MotorCalificacionRiesgo.calcular(config(SENSIBLE, "100"),
                List.of(servicio("Antecedentes", "100", true, 20)));
        assertThat(r.puntajeFinal()).isEqualByComparingTo("20.0");
        assertThat(r.nivelFinal()).isEqualTo(NivelRiesgo.BAJO);
        assertThat(r.tipoAjuste()).isEqualTo(TipoAjuste.NINGUNO);
        assertThat(r.requiereRevisionManual()).isFalse();
        assertThat(r.cobertura()).isEqualByComparingTo("100");
    }

    // ── Apoyo ──

    static ModeloRiesgo.ConfiguracionCalculo config(Umbrales umbrales, String cobertura) {
        return new ModeloRiesgo.ConfiguracionCalculo(umbrales, PISOS, bd(cobertura), "prueba");
    }

    /** bruto: número (un hallazgo con esos puntos), NR (no realizado) o CRIT0 (crítico con 0 puntos). */
    static ServicioEvaluado servicio(String nombre, String peso, boolean obligatorio, Object bruto) {
        if (NR.equals(bruto)) {
            return new ServicioEvaluado(nombre.toUpperCase(), nombre, bd(peso), obligatorio, EstadoSubproceso.NO_REALIZADO, List.of());
        }
        List<Hallazgo> hallazgos = CRIT0.equals(bruto)
                ? List.of(new Hallazgo("Título académico falso", BigDecimal.ZERO, true))
                : List.of(new Hallazgo("Hallazgo de prueba", bd(String.valueOf(bruto)), false));
        return new ServicioEvaluado(nombre.toUpperCase(), nombre, bd(peso), obligatorio, EstadoSubproceso.REALIZADO, hallazgos);
    }

    static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
