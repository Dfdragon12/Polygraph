package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.pagos.entity.LotePrepago;
import com.polygraph.erp.modules.pagos.entity.OrdenCompraItem;
import com.polygraph.erp.modules.pagos.entity.SaldoServicioCliente;
import com.polygraph.erp.modules.pagos.repository.LotePrepagoRepository;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraItemRepository;
import com.polygraph.erp.modules.pagos.repository.SaldoServicioClienteRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.tienda.service.ParametrosTiendaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cierre · caso 2: 1 Plan Básico + 2 Plan Inicial de Antecedentes = 7 unidades: un lote de 5 a $38.000 y dos lotes de 1 a $40.000 (un lote por paquete). */
class CierreLotesPorPaqueteTest {

    private final Proceso antecedentes = Proceso.builder().idProceso(1).nombreProceso("Antecedentes").build();
    private final Cliente cliente = Cliente.builder().idCliente(1).build();
    private SaldoServicioClienteRepository saldos;
    private LotePrepagoRepository lotes;
    private SaldoServicioClienteService servicio;
    private SaldoServicioCliente saldo;

    @BeforeEach
    void setUp() {
        saldos = mock(SaldoServicioClienteRepository.class);
        lotes = mock(LotePrepagoRepository.class);
        saldo = SaldoServicioCliente.builder().cliente(cliente).proceso(antecedentes).cantidadComprada(0).cantidadConsumida(0).cantidadDisponible(0).build();
        when(saldos.findByCliente_IdClienteAndProceso_IdProceso(1, 1)).thenReturn(Optional.of(saldo));
        servicio = new SaldoServicioClienteService(saldos, new BolsaPrepagoService(saldos, mock(OrdenCompraItemRepository.class)), lotes,
                Clock.fixed(Instant.parse("2026-11-01T15:00:00Z"), ZoneOffset.UTC), mock(ParametrosTiendaService.class));
    }

    private OrdenCompraItem item(long id, int unidades, int paquetes, String unitario, int meses) {
        return OrdenCompraItem.builder().idItem(id).cantidad(unidades * paquetes).unidadesPlan(unidades).cantidadPaquetes(paquetes)
                .valorUnitario(new BigDecimal(unitario)).valorOriginal(new BigDecimal("40000")).cobertura("LOCAL").vigenciaMeses(meses).build();
    }

    @Test
    @DisplayName("1 Plan Básico + 2 Plan Inicial: 7 unidades; un lote de 5 a $38.000 y dos lotes de 1 a $40.000, cada uno con su vencimiento")
    void unLotePorPaquete() {
        servicio.acreditar(cliente, antecedentes, 5, item(10, 5, 1, "38000", 3), "WOMPI-1");
        servicio.acreditar(cliente, antecedentes, 2, item(11, 1, 2, "40000", 3), "WOMPI-1");

        ArgumentCaptor<LotePrepago> c = ArgumentCaptor.forClass(LotePrepago.class);
        verify(lotes, times(3)).save(c.capture());
        List<LotePrepago> creados = c.getAllValues();
        assertThat(creados).extracting(LotePrepago::getCantidadComprada).containsExactly(5, 1, 1);
        assertThat(creados).extracting(l -> l.getValorUnitario().intValue()).containsExactly(38000, 40000, 40000);
        assertThat(creados).extracting(LotePrepago::getIndicePaquete).containsExactly(0, 0, 1);
        assertThat(creados).allSatisfy(l -> assertThat(l.getFechaVencimiento().toLocalDate()).isEqualTo(java.time.LocalDate.of(2027, 2, 1)));
        assertThat(saldo.getCantidadComprada()).isEqualTo(7);
        assertThat(saldo.getCantidadDisponible()).isEqualTo(7);
    }

    @Test
    @DisplayName("Un mismo aviso de pago otra vez no crea ninguno de los lotes de nuevo")
    void avisoRepetido() {
        var it = item(11, 1, 2, "40000", 3);
        servicio.acreditar(cliente, antecedentes, 2, it, "WOMPI-2");
        when(lotes.existsByItem_IdItem(11L)).thenReturn(true);
        servicio.acreditar(cliente, antecedentes, 2, it, "WOMPI-2");
        verify(lotes, times(2)).save(any(LotePrepago.class));   // solo los dos del primer aviso
        assertThat(saldo.getCantidadComprada()).isEqualTo(2);
    }

    @Test
    @DisplayName("Una compra sin plan (cantidad libre) sigue siendo un solo lote")
    void sinPlanUnSoloLote() {
        var it = OrdenCompraItem.builder().idItem(12L).cantidad(4).valorUnitario(new BigDecimal("40000")).valorOriginal(new BigDecimal("40000")).build();
        servicio.acreditar(cliente, antecedentes, 4, it, "WOMPI-3");
        ArgumentCaptor<LotePrepago> c = ArgumentCaptor.forClass(LotePrepago.class);
        verify(lotes).save(c.capture());
        assertThat(c.getValue().getCantidadComprada()).isEqualTo(4);
    }
}
