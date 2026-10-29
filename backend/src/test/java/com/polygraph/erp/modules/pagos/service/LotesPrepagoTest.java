package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.pagos.entity.LotePrepago;
import com.polygraph.erp.modules.pagos.entity.OrdenCompraItem;
import com.polygraph.erp.modules.pagos.entity.SaldoServicioCliente;
import com.polygraph.erp.modules.pagos.repository.LotePrepagoRepository;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraItemRepository;
import com.polygraph.erp.modules.pagos.repository.SaldoServicioClienteRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** NS-02: lotes por compra, descuento FIFO y valor unitario que no cambia con compras nuevas. */
class LotesPrepagoTest {

    private final Proceso proceso = Proceso.builder().idProceso(10).nombreProceso("Antecedentes").build();
    private SaldoServicioClienteRepository saldos;
    private LotePrepagoRepository lotes;
    private SaldoServicioClienteService servicio;

    @BeforeEach
    void setUp() {
        saldos = mock(SaldoServicioClienteRepository.class);
        lotes = mock(LotePrepagoRepository.class);
        var bolsa = new BolsaPrepagoService(saldos, mock(OrdenCompraItemRepository.class));
        servicio = new SaldoServicioClienteService(saldos, bolsa, lotes);
    }

    private LotePrepago lote(long id, int comprada, int consumida, String valor, int dia) {
        return LotePrepago.builder().idLote(id).proceso(proceso).cantidadComprada(comprada).cantidadConsumida(consumida)
                .valorUnitario(new BigDecimal(valor)).fechaCompra(LocalDateTime.of(2026, 10, dia, 9, 0)).build();
    }

    private void saldo(int comprada, int consumida) {
        var s = SaldoServicioCliente.builder().cliente(Cliente.builder().idCliente(1).build()).proceso(proceso)
                .cantidadComprada(comprada).cantidadConsumida(consumida).cantidadDisponible(comprada - consumida).build();
        when(saldos.findByCliente_IdClienteAndProceso_IdProceso(1, 10)).thenReturn(Optional.of(s));
        when(saldos.descontarUna(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(10), any())).thenReturn(comprada - consumida >= 1 ? 1 : 0);
    }

    @Test
    @DisplayName("FIFO: la unidad sale del lote más antiguo que todavía tenga, con el valor de ESA compra")
    void saleDelLoteMasAntiguo() {
        var viejo = lote(1, 5, 5, "40000", 1);          // agotado
        var medio = lote(2, 10, 3, "38000", 10);        // 7 restantes
        var nuevo = lote(3, 20, 0, "45000", 20);
        saldo(35, 8);
        when(lotes.bloquearPorClienteYProceso(1, 10)).thenReturn(List.of(viejo, medio, nuevo));

        var u = servicio.descontar(1, proceso);

        assertThat(u.idLote()).isEqualTo(2L);
        assertThat(u.valorUnitario()).isEqualByComparingTo("38000");
        assertThat(medio.getCantidadConsumida()).isEqualTo(4);
        assertThat(nuevo.getCantidadConsumida()).isZero();
    }

    @Test
    @DisplayName("Al agotarse un lote, la siguiente unidad pasa al siguiente y toma su valor")
    void pasaAlSiguienteLote() {
        var viejo = lote(1, 2, 1, "40000", 1);
        var nuevo = lote(2, 5, 0, "45000", 20);
        saldo(7, 1);
        when(lotes.bloquearPorClienteYProceso(1, 10)).thenReturn(List.of(viejo, nuevo));

        assertThat(servicio.descontar(1, proceso).valorUnitario()).isEqualByComparingTo("40000");   // última del primer lote
        assertThat(servicio.descontar(1, proceso).valorUnitario()).isEqualByComparingTo("45000");   // ya del segundo
    }

    @Test
    @DisplayName("Sin lote que la respalde (saldo anterior a los lotes) la unidad se descuenta igual, sin lote ni valor")
    void sinLote() {
        saldo(3, 0);
        when(lotes.bloquearPorClienteYProceso(1, 10)).thenReturn(List.of());
        var u = servicio.descontar(1, proceso);
        assertThat(u.idLote()).isNull();
        assertThat(u.valorUnitario()).isNull();
    }

    @Test
    @DisplayName("Una compra aprobada crea su lote con la cantidad y el valor del ítem; aprobarla dos veces no duplica el lote")
    void acreditarCreaElLote() {
        saldo(0, 0);
        var item = OrdenCompraItem.builder().idItem(7L).cantidad(20).valorUnitario(new BigDecimal("38000")).valorOriginal(new BigDecimal("40000"))
                .nombreDescuentoAplicado("Volumen").build();
        when(lotes.findByItem_IdItem(7L)).thenReturn(Optional.empty());
        servicio.acreditar(Cliente.builder().idCliente(1).build(), proceso, 20, item);
        org.mockito.Mockito.verify(lotes).save(org.mockito.ArgumentMatchers.argThat((LotePrepago l) ->
                l.getCantidadComprada() == 20 && l.getCantidadConsumida() == 0 && l.getValorUnitario().compareTo(new BigDecimal("38000")) == 0));

        when(lotes.findByItem_IdItem(7L)).thenReturn(Optional.of(lote(9, 20, 0, "38000", 1)));
        servicio.acreditar(Cliente.builder().idCliente(1).build(), proceso, 20, item);
        org.mockito.Mockito.verify(lotes, org.mockito.Mockito.times(1)).save(any(LotePrepago.class));
    }

    @Test
    void restanteDelLote() {
        assertThat(lote(1, 10, 4, "1", 1).restante()).isEqualTo(6);
    }

    @Test
    @DisplayName("Sin saldo (la sentencia atómica no actualiza ninguna fila) se rechaza y no se toca ningún lote")
    void sinSaldoNoDescuenta() {
        saldo(5, 5);
        var lleno = lote(1, 5, 5, "40000", 1);
        when(lotes.bloquearPorClienteYProceso(1, 10)).thenReturn(List.of(lleno));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> servicio.descontar(1, proceso))
                .isInstanceOf(com.polygraph.erp.shared.exceptions.ApiException.class);
        assertThat(lleno.getCantidadConsumida()).isEqualTo(5);
    }
}
