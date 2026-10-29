package com.polygraph.erp.modules.solicitudes.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** NS-11: lo que dice el paso 3 sale del servidor. Casos de aceptación: Carlos (prepago) y Acme sas (pospago). */
class ResumenCobroServiceTest {

    @Test
    void prepago_antecedentes_de_carlos_dice_disponibles_18_quedaran_17() {
        var r = ResumenCobroService.prepago("Antecedentes", "Estudios", 18, BigDecimal.ZERO);
        assertThat(r.alcanza()).isTrue();
        assertThat(r.disponiblesAhora()).isEqualTo(18);
        assertThat(r.quedaran()).isEqualTo(17);
        assertThat(r.mensaje()).isEqualTo("Se usará 1 Antecedentes de tu bolsa · Disponibles ahora: 18 · Quedarán: 17");
    }

    @Test
    void prepago_sin_unidades_no_alcanza_y_no_dice_que_quedan_menos_uno() {
        var r = ResumenCobroService.prepago("Visita Domiciliaria Presencial", "Visitas", 0, BigDecimal.ZERO);
        assertThat(r.alcanza()).isFalse();
        assertThat(r.quedaran()).isZero();
        assertThat(r.mensaje()).contains("No tienes").doesNotContain("Quedarán");
    }

    @Test
    void pospago_antecedentes_de_acme_con_8830000_queda_8790000() {
        var r = ResumenCobroService.pospago("Antecedentes", "Estudios", new BigDecimal("40000"), BigDecimal.ZERO, new BigDecimal("8830000"));
        assertThat(r.alcanza()).isTrue();
        assertThat(r.cupoQuedara()).isEqualByComparingTo("8790000");
        assertThat(r.mensaje()).isEqualTo("Valor del servicio: $40.000 · Cupo disponible: $8.830.000 · Quedará: $8.790.000 · Queda sin facturar hasta tu próximo corte");
    }

    @Test
    void pospago_el_recargo_por_ciudad_se_suma_al_cargo_igual_que_al_descontar() {
        var r = ResumenCobroService.pospago("Poligrafía", "Poligrafías", new BigDecimal("100000"), new BigDecimal("15000"), new BigDecimal("1000000"));
        assertThat(r.cupoQuedara()).isEqualByComparingTo("885000");
        assertThat(r.mensaje()).contains("Recargo por ciudad: $15.000").contains("Quedará: $885.000");
    }

    @Test
    void pospago_si_no_alcanza_el_cupo_lo_dice() {
        var r = ResumenCobroService.pospago("Poligrafía", "Poligrafías", new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("60000"));
        assertThat(r.alcanza()).isFalse();
        assertThat(r.mensaje()).contains("No alcanza").doesNotContain("Quedará");
    }
}
