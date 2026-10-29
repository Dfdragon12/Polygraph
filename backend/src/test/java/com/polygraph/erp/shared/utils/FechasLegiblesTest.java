package com.polygraph.erp.shared.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FechasLegibles — fechas para leer en un mensaje")
class FechasLegiblesTest {

    @Test
    @DisplayName("23 de septiembre de 2026, no 2026-09-23; el día sin cero a la izquierda y el mes en minúscula")
    void fechaLarga() {
        assertThat(FechasLegibles.larga(LocalDate.of(2026, 9, 23))).isEqualTo("23 de septiembre de 2026");
        assertThat(FechasLegibles.larga(LocalDate.of(2026, 1, 5))).isEqualTo("5 de enero de 2026");
        assertThat(FechasLegibles.larga(LocalDate.of(2027, 12, 31))).isEqualTo("31 de diciembre de 2027");
    }

    @Test
    @DisplayName("Los doce meses salen en español")
    void mesesEnEspanol() {
        String[] meses = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
        for (int m = 1; m <= 12; m++) assertThat(FechasLegibles.larga(LocalDate.of(2026, m, 10))).isEqualTo("10 de " + meses[m - 1] + " de 2026");
    }

    @Test
    @DisplayName("Sin fecha dice \"sin fecha\"")
    void sinFecha() {
        assertThat(FechasLegibles.larga(null)).isEqualTo("sin fecha");
    }
}
