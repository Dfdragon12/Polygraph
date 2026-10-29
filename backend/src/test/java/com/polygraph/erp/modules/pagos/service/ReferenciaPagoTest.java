package com.polygraph.erp.modules.pagos.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenciaPagoTest {

    @Test
    void cadaIntentoLlevaUnaReferenciaDistintaPeroConLaMismaBase() {
        String base = "PYG-ABCDEF0123456789ABCD";
        String primero = ReferenciaPago.nuevoIntento(base, 1000L);
        String segundo = ReferenciaPago.nuevoIntento(primero, 2000L);

        assertThat(primero).isEqualTo(base + "-R1000").isNotEqualTo(base);
        assertThat(segundo).isEqualTo(base + "-R2000").isNotEqualTo(primero);
        assertThat(ReferenciaPago.base(primero)).isEqualTo(base);
        assertThat(ReferenciaPago.base(segundo)).isEqualTo(base);
    }

    @Test
    void laBaseDeUnaReferenciaSinSufijoEsElla() {
        assertThat(ReferenciaPago.base("PS-ABC123-1700000000000")).isEqualTo("PS-ABC123-1700000000000");
        assertThat(ReferenciaPago.base(null)).isNull();
    }

    @Test
    void cabeEnLaColumnaDeLaOrden() {
        // ordenes_compra.referencia mide 50 caracteres
        String referencia = ReferenciaPago.nuevoIntento("PYG-ABCDEF0123456789ABCD");
        assertThat(referencia.length()).isLessThanOrEqualTo(50);
    }
}
