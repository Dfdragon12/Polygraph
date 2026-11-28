package com.polygraph.erp.modules.pagos.mora;

import com.polygraph.erp.modules.pagos.mora.MoraCliente.Factura;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** NS-05: la mora sale de las facturas. */
class MoraClienteTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);

    private Factura f(String ref, LocalDate vence, String monto, boolean pagada) {
        return new Factura(ref, vence, new BigDecimal(monto), pagada);
    }

    @Test
    @DisplayName("Sin facturas, o con todas pagadas, no hay mora")
    void sinMora() {
        assertThat(MoraCliente.calcular(List.of(), HOY).enMora()).isFalse();
        assertThat(MoraCliente.calcular(List.of(f("F-1", HOY.minusDays(40), "100", true)), HOY).enMora()).isFalse();
    }

    @Test
    @DisplayName("Una factura que vence hoy todavía no está vencida; la de ayer sí")
    void elDiaDelVencimiento() {
        assertThat(MoraCliente.calcular(List.of(f("F-1", HOY, "100", false)), HOY).enMora()).isFalse();
        var r = MoraCliente.calcular(List.of(f("F-1", HOY.minusDays(1), "100", false)), HOY);
        assertThat(r.enMora()).isTrue();
        assertThat(r.diasMora()).isEqualTo(1);
    }

    @Test
    @DisplayName("Pagar una de dos facturas vencidas NO saca al cliente de la mora")
    void pagarUnaDeDos() {
        var dos = List.of(f("F-1", HOY.minusDays(30), "1000000", false), f("F-2", HOY.minusDays(5), "200000", false));
        assertThat(MoraCliente.calcular(dos, HOY).enMora()).isTrue();
        var pagadaLaVieja = List.of(f("F-1", HOY.minusDays(30), "1000000", true), f("F-2", HOY.minusDays(5), "200000", false));
        var r = MoraCliente.calcular(pagadaLaVieja, HOY);
        assertThat(r.enMora()).isTrue();
        assertThat(r.referenciaMasAntigua()).isEqualTo("F-2");
        assertThat(MoraCliente.calcular(List.of(f("F-1", HOY.minusDays(30), "1", true), f("F-2", HOY.minusDays(5), "1", true)), HOY).enMora()).isFalse();
    }

    @Test
    @DisplayName("Un solo mensaje: cuántas facturas, cuánto, la más antigua y hace cuántos días")
    void mensajeUnico() {
        var r = MoraCliente.calcular(List.of(f("F-1", HOY.minusDays(30), "1000000", false), f("F-2", HOY.minusDays(5), "200000", false)), HOY);
        assertThat(r.facturasVencidas()).isEqualTo(2);
        assertThat(r.montoVencido()).isEqualByComparingTo("1200000");
        assertThat(r.diasMora()).isEqualTo(30);
        assertThat(r.mensaje()).isEqualTo("Tu cuenta está en mora: tienes 2 facturas vencidas por $1.200.000. La más antigua, F-1, venció hace 30 días. "
                + "Págalas para volver a solicitar servicios.");
        var una = MoraCliente.calcular(List.of(f("F-9", HOY.minusDays(1), "40000", false)), HOY);
        assertThat(una.mensaje()).isEqualTo("Tu cuenta está en mora: tienes 1 factura vencida por $40.000. La factura F-9 venció hace 1 día. Págalas para volver a solicitar servicios.");
    }
}
