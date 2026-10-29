package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.pagos.entity.LotePrepago;
import com.polygraph.erp.modules.pagos.entity.MovimientoCreditoPospago;
import com.polygraph.erp.modules.pagos.entity.FacturaPospago;
import com.polygraph.erp.modules.pagos.repository.LotePrepagoRepository;
import com.polygraph.erp.modules.pagos.repository.MovimientoCreditoPospagoRepository;
import com.polygraph.erp.modules.pagos.repository.SaldoServicioClienteRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.shared.enums.TipoCliente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** NS-04: cancelar devuelve la unidad al mismo lote (prepago) o el cupo (pospago), una sola vez, y lo cancelado no se factura. */
class DevolucionCobroServiceTest {

    private SaldoServicioClienteRepository saldos;
    private LotePrepagoRepository lotes;
    private MovimientoCreditoPospagoRepository movimientos;
    private ClientePospagoRepository pospagos;
    private DevolucionCobroService servicio;
    private final Proceso proceso = Proceso.builder().idProceso(10).nombreProceso("Antecedentes").build();

    @BeforeEach
    void setUp() {
        saldos = mock(SaldoServicioClienteRepository.class);
        lotes = mock(LotePrepagoRepository.class);
        movimientos = mock(MovimientoCreditoPospagoRepository.class);
        pospagos = mock(ClientePospagoRepository.class);
        servicio = new DevolucionCobroService(saldos, lotes, movimientos, pospagos, mock(SaldoServicioClienteService.class));
    }

    private Servicio solicitud(TipoCliente tipo, Long lote) {
        return Servicio.builder().idServicio(39).proceso(proceso).idLotePrepago(lote)
                .cliente(Cliente.builder().idCliente(1).tipoCliente(tipo).build()).build();
    }

    @Test
    @DisplayName("Prepago: la unidad vuelve a la bolsa y al MISMO lote; devolver otra vez no hace nada")
    void prepagoMismoLote() {
        var lote = LotePrepago.builder().idLote(7L).cantidadComprada(10).cantidadConsumida(4).valorUnitario(new BigDecimal("40000")).build();
        when(lotes.findById(7L)).thenReturn(Optional.of(lote));
        when(saldos.devolverUna(eq(1), eq(10), any())).thenReturn(1);
        var s = solicitud(TipoCliente.PREPAGO, 7L);

        assertThat(servicio.devolver(s)).isTrue();
        assertThat(lote.getCantidadConsumida()).isEqualTo(3);
        assertThat(s.getCobroDevueltoEn()).isNotNull();

        assertThat(servicio.devolver(s)).isFalse();
        verify(saldos, times(1)).devolverUna(eq(1), eq(10), any());
        assertThat(lote.getCantidadConsumida()).isEqualTo(3);
    }

    @Test
    @DisplayName("Prepago sin lote (solicitud anterior a los lotes): la unidad vuelve a la bolsa")
    void prepagoSinLote() {
        when(saldos.devolverUna(eq(1), eq(10), any())).thenReturn(1);
        assertThat(servicio.devolver(solicitud(TipoCliente.PREPAGO, null))).isTrue();
        verify(lotes, never()).save(any());
    }

    @Test
    @DisplayName("Pospago sin facturar: el cargo se anula (no se factura) y el cupo vuelve")
    void pospagoSinFacturar() {
        var mov = MovimientoCreditoPospago.builder().montoTotal(new BigDecimal("50000")).build();
        var cupo = ClientePospago.builder().creditoDisponible(new BigDecimal("100000")).build();
        when(movimientos.findFirstByServicio_IdServicioOrderByFechaGeneradoDesc(39)).thenReturn(Optional.of(mov));
        when(pospagos.findByIdCliente(1)).thenReturn(Optional.of(cupo));
        var s = solicitud(TipoCliente.POSPAGO, null);

        assertThat(servicio.devolver(s)).isTrue();
        assertThat(mov.getAnuladoEn()).isNotNull();
        assertThat(cupo.getCreditoDisponible()).isEqualByComparingTo("150000");
        assertThat(s.getCobroDevueltoEn()).isNotNull();
    }

    @Test
    @DisplayName("Pospago ya facturado: no se toca (hace falta una nota crédito) y no se marca como devuelto")
    void pospagoYaFacturado() {
        var mov = MovimientoCreditoPospago.builder().montoTotal(new BigDecimal("50000")).factura(FacturaPospago.builder().referencia("F-1").build()).build();
        when(movimientos.findFirstByServicio_IdServicioOrderByFechaGeneradoDesc(39)).thenReturn(Optional.of(mov));
        var s = solicitud(TipoCliente.POSPAGO, null);

        assertThat(servicio.devolver(s)).isFalse();
        assertThat(mov.getAnuladoEn()).isNull();
        assertThat(s.getCobroDevueltoEn()).isNull();
    }

    @Test
    @DisplayName("Revertir la cancelación de un pospago vuelve a cobrar el cargo contra el cupo")
    void revertirPospago() {
        var mov = MovimientoCreditoPospago.builder().montoTotal(new BigDecimal("50000")).anuladoEn(java.time.LocalDateTime.now()).build();
        var cupo = ClientePospago.builder().creditoDisponible(new BigDecimal("150000")).build();
        when(movimientos.findFirstByServicio_IdServicioOrderByFechaGeneradoDesc(39)).thenReturn(Optional.of(mov));
        when(pospagos.findByIdCliente(1)).thenReturn(Optional.of(cupo));
        var s = solicitud(TipoCliente.POSPAGO, null);
        s.setCobroDevueltoEn(java.time.LocalDateTime.now());

        servicio.volverACobrar(s);

        assertThat(mov.getAnuladoEn()).isNull();
        assertThat(cupo.getCreditoDisponible()).isEqualByComparingTo("100000");
        assertThat(s.getCobroDevueltoEn()).isNull();
    }
}
