package com.polygraph.erp.modules.formularios.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class NumeracionProtocoloTest {

    @Test
    void sindicalApagadaNumeraDel1Al16SinSaltos() {
        Map<Integer, Integer> n = NumeracionProtocolo.numeros(false);
        assertThat(n).doesNotContainKey(10);
        assertThat(n.get(9)).isEqualTo(9);
        assertThat(n.get(11)).isEqualTo(10);
        assertThat(n.get(12)).isEqualTo(11);
        assertThat(n.get(17)).isEqualTo(16);
        assertThat(n.values()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
    }

    @Test
    void sindicalEncendidaNumeraDel1Al17ConLaSindicalEnLaPosicion10() {
        Map<Integer, Integer> n = NumeracionProtocolo.numeros(true);
        assertThat(n.get(10)).isEqualTo(10);
        assertThat(n.get(11)).isEqualTo(11);
        assertThat(n.get(17)).isEqualTo(17);
    }

    @Test
    void marcadoresDelWordSiguenLasClavesNoElNumero() {
        Map<String, Object> m = NumeracionProtocolo.marcadores(false);
        assertThat(m.get("ps_10_num")).isEqualTo("");
        assertThat(m.get("ps_11_num")).isEqualTo("10");
        assertThat(m.get("ps_17_num")).isEqualTo("16");
        // Las preguntas 13 y 14 (claves) se muestran como 12 y 13.
        assertThat(m.get("ps_comparadas")).isEqualTo("1, 2, 3, 12 y 13");
        assertThat(NumeracionProtocolo.marcadores(true).get("ps_comparadas")).isEqualTo("1, 2, 3, 13 y 14");
    }
}
