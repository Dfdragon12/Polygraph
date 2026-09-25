package com.polygraph.erp.modules.riesgo.motor;

import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static com.polygraph.erp.modules.riesgo.motor.MotorCalificacionRiesgoAceptacionTest.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Reglas puntuales del motor: bordes de nivel, redondeo, tope, crítico, peso 0 y estados sin
 *  resultado final (EN_PROCESO / SIN_INFORMACION / NO_CALIFICA). */
@DisplayName("Motor de riesgo — bordes y reglas puntuales")
class MotorCalificacionRiesgoReglasTest {

    @ParameterizedTest(name = "{0}: {1} → {2}")
    @CsvSource({
            "ESTANDAR, 30.0, BAJO", "ESTANDAR, 30.1, MEDIO", "ESTANDAR, 60.0, MEDIO", "ESTANDAR, 60.1, ALTO",
            "SENSIBLE, 25.0, BAJO", "SENSIBLE, 25.1, MEDIO", "SENSIBLE, 50.0, MEDIO", "SENSIBLE, 50.1, ALTO",
            "CRITICO, 20.0, BAJO", "CRITICO, 20.1, MEDIO", "CRITICO, 40.0, MEDIO", "CRITICO, 40.1, ALTO",
            // Redondeo a 1 decimal HALF_UP ANTES de clasificar
            "ESTANDAR, 30.04, BAJO", "ESTANDAR, 30.05, MEDIO",
    })
    void bordesDeNivel(String criticidad, String puntaje, NivelRiesgo esperado) {
        Umbrales u = switch (criticidad) { case "SENSIBLE" -> SENSIBLE; case "CRITICO" -> CRITICO; default -> ESTANDAR; };
        assertThat(ClasificadorNivel.clasificar(new BigDecimal(puntaje), u)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("Servicio sin hallazgos = 0 BAJO")
    void servicioSinHallazgos() {
        ResultadoServicio r = CalificadorServicio.calificar(
                new ServicioEvaluado("A", "A", bd("100"), true, EstadoSubproceso.REALIZADO, List.of()), ESTANDAR);
        assertThat(r.puntajeBruto()).isEqualByComparingTo("0");
        assertThat(r.nivel()).isEqualTo(NivelRiesgo.BAJO);
    }

    @Test
    @DisplayName("Suma mayor a 100 queda en 100")
    void topeEn100() {
        ResultadoServicio r = CalificadorServicio.calificar(new ServicioEvaluado("A", "A", bd("100"), true, EstadoSubproceso.REALIZADO,
                List.of(new Hallazgo("x", bd("70"), false), new Hallazgo("y", bd("60"), false))), ESTANDAR);
        assertThat(r.puntajeBruto()).isEqualByComparingTo("100");
        assertThat(r.nivel()).isEqualTo(NivelRiesgo.ALTO);
    }

    @Test
    @DisplayName("Servicio con un crítico = 100 ALTO aunque sume poco")
    void servicioConCritico() {
        ResultadoServicio r = CalificadorServicio.calificar(new ServicioEvaluado("A", "A", bd("100"), true, EstadoSubproceso.REALIZADO,
                List.of(new Hallazgo("x", bd("5"), false), new Hallazgo("Orden de captura", bd("0"), true))), ESTANDAR);
        assertThat(r.puntajeBruto()).isEqualByComparingTo("5");
        assertThat(r.puntajeFinal()).isEqualByComparingTo("100");
        assertThat(r.nivel()).isEqualTo(NivelRiesgo.ALTO);
    }

    @Test
    @DisplayName("Con un subproceso PENDIENTE no hay resultado final (EN_PROCESO), pero sí el de los realizados")
    void pendiente_enProceso() {
        ResultadoCalificacion r = MotorCalificacionRiesgo.calcular(config(ESTANDAR, "60"), List.of(
                servicio("Antecedentes", "60", true, 40),
                new ServicioEvaluado("POL", "Poligrafía", bd("40"), true, EstadoSubproceso.PENDIENTE, List.of())));
        assertThat(r.estado()).isEqualTo(EstadoResultado.EN_PROCESO);
        assertThat(r.nivelFinal()).isNull();
        assertThat(r.requiereRevisionManual()).isFalse();
        assertThat(r.servicios().get(0).puntajeFinal()).isEqualByComparingTo("40.0");
        assertThat(r.servicios().get(0).nivel()).isEqualTo(NivelRiesgo.MEDIO);
    }

    @Test
    @DisplayName("Todo NO_REALIZADO → SIN_INFORMACION con revisión manual")
    void todoNoRealizado_sinInformacion() {
        ResultadoCalificacion r = MotorCalificacionRiesgo.calcular(config(ESTANDAR, "60"), List.of(
                servicio("Antecedentes", "60", true, NR), servicio("Poligrafía", "40", true, NR)));
        assertThat(r.estado()).isEqualTo(EstadoResultado.SIN_INFORMACION);
        assertThat(r.requiereRevisionManual()).isTrue();
    }

    @Test
    @DisplayName("Pesos que no suman 100 o subprocesos sin peso → NO_CALIFICA")
    void configuracionInvalida_noCalifica() {
        ResultadoCalificacion suma90 = MotorCalificacionRiesgo.calcular(config(ESTANDAR, "60"), List.of(
                servicio("Antecedentes", "50", true, 10), servicio("Poligrafía", "40", true, 10)));
        assertThat(suma90.estado()).isEqualTo(EstadoResultado.NO_CALIFICA);
        assertThat(suma90.motivosRevision()).containsExactly("Los pesos suman 90,0 % (deben sumar 100 %)");

        ResultadoCalificacion sinPeso = MotorCalificacionRiesgo.calcular(config(ESTANDAR, "60"), List.of(
                servicio("Antecedentes", "100", true, 10),
                new ServicioEvaluado("X", "Otro", null, false, EstadoSubproceso.REALIZADO, List.of())));
        assertThat(sinPeso.estado()).isEqualTo(EstadoResultado.NO_CALIFICA);
        assertThat(sinPeso.motivosRevision()).containsExactly("1 subproceso sin peso");
    }

    @Test
    @DisplayName("Peso 0: una respuesta crítica igual lleva a ALTO (decisión 25-sep-2026)")
    void pesoCero_criticoCuenta() {
        ResultadoCalificacion r = MotorCalificacionRiesgo.calcular(config(ESTANDAR, "60"), List.of(
                servicio("Antecedentes", "100", true, 0),
                new ServicioEvaluado("PSI", "Psicotécnicas", bd("0"), false, EstadoSubproceso.REALIZADO,
                        List.of(new Hallazgo("Indicios de actividades ilícitas", bd("0"), true)))));
        assertThat(r.nivelFinal()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(r.tipoAjuste()).isEqualTo(TipoAjuste.CRITICO);
        assertThat(r.motivosAjuste()).extracting(MotivoAjuste::servicio).containsExactly("Psicotécnicas");
    }

    @Test
    @DisplayName("Peso 0: no participa en el promedio ni en las reglas de piso")
    void pesoCero_fueraDePromedioYPisos() {
        ResultadoCalificacion r = MotorCalificacionRiesgo.calcular(config(ESTANDAR, "60"), List.of(
                servicio("Antecedentes", "100", true, 10),
                servicio("Psicotécnicas", "0", false, 100)));
        assertThat(r.puntajePonderado()).isEqualByComparingTo("10.0");
        assertThat(r.tipoAjuste()).isEqualTo(TipoAjuste.NINGUNO); // 100 en peso 0 no dispara la regla A
        assertThat(r.nivelFinal()).isEqualTo(NivelRiesgo.BAJO);
        assertThat(r.servicios().get(1).pesoEfectivo()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Cobertura menor a la mínima → revisión manual")
    void coberturaInsuficiente() {
        ResultadoCalificacion r = MotorCalificacionRiesgo.calcular(config(ESTANDAR, "60"), List.of(
                servicio("Antecedentes", "50", false, 0), servicio("Visita", "50", false, NR)));
        assertThat(r.cobertura()).isEqualByComparingTo("50");
        assertThat(r.nivelFinal()).isEqualTo(NivelRiesgo.BAJO);
        assertThat(r.motivosRevision()).containsExactly("Cobertura 50,0 % menor a la mínima 60,0 %");
    }
}
