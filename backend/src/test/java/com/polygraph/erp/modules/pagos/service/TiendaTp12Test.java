package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.pagos.entity.LotePrepago;
import com.polygraph.erp.modules.pagos.entity.MovimientoCreditoPospago;
import com.polygraph.erp.modules.pagos.repository.FacturaPospagoRepository;
import com.polygraph.erp.modules.pagos.repository.LotePrepagoRepository;
import com.polygraph.erp.modules.pagos.repository.MovimientoCreditoPospagoRepository;
import com.polygraph.erp.modules.pagos.repository.PagoWompiRepository;
import com.polygraph.erp.modules.pagos.repository.SaldoServicioClienteRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.tienda.service.IvaService;
import com.polygraph.erp.modules.tienda.service.ParametrosTiendaService;
import com.polygraph.erp.modules.tienda.service.PrecioPospagoService;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TP-12: nueva solicitud pospago: cupo con bloqueo, recálculo en el servidor, origen del precio, bolsa de un cliente que vino de prepago y cancelación. */
class TiendaTp12Test {

    private ClientePospagoRepository pospagoRepo;
    private MovimientoCreditoPospagoRepository movimientos;
    private RecargoPendienteService recargos;
    private PrecioPospagoService precioPospago;
    private ClientePospagoService servicio;
    private ClientePospago cupo;
    private final Cliente cliente = Cliente.builder().idCliente(9).tipoCliente(TipoCliente.POSPAGO).build();
    private final Ciudad tunja = mock(Ciudad.class);
    private final Proceso preEmpleo = Proceso.builder().idProceso(7).nombreProceso("Poligrafía Pre Empleo").valor(new BigDecimal("168000")).aplicaIva(true).build();

    @BeforeEach
    void setUp() {
        pospagoRepo = mock(ClientePospagoRepository.class);
        movimientos = mock(MovimientoCreditoPospagoRepository.class);
        recargos = mock(RecargoPendienteService.class);
        precioPospago = mock(PrecioPospagoService.class);
        when(precioPospago.precioParaCobrar(preEmpleo)).thenReturn(new BigDecimal("168000"));
        when(precioPospago.codigoOrigen(preEmpleo)).thenReturn("PLAN_100");
        var ciudades = mock(CiudadRepository.class);
        when(ciudades.findById(5)).thenReturn(Optional.of(tunja));
        when(recargos.calcular(preEmpleo, tunja)).thenReturn(new BigDecimal("15000"));
        var iva = mock(IvaService.class);
        when(iva.porcentaje()).thenReturn(new BigDecimal("19"));
        cupo = ClientePospago.builder().idCliente(9).limiteCredito(new BigDecimal("9000000")).creditoDisponible(new BigDecimal("8830000")).build();
        when(pospagoRepo.findByIdCliente(9)).thenReturn(Optional.of(cupo));
        when(pospagoRepo.bloquearPorIdCliente(9)).thenReturn(Optional.of(cupo));
        servicio = new ClientePospagoService(pospagoRepo, movimientos, mock(FacturaPospagoRepository.class), mock(PagoWompiRepository.class), ciudades,
                recargos, mock(UsuarioRepository.class), mock(NotificacionRepository.class), iva, precioPospago);
    }

    private MovimientoCreditoPospago ultimoMovimiento() {
        ArgumentCaptor<MovimientoCreditoPospago> cap = ArgumentCaptor.forClass(MovimientoCreditoPospago.class);
        verify(movimientos).save(cap.capture());
        return cap.getValue();
    }

    @Test
    @DisplayName("Cupo $8.830.000, Pre Empleo en Tunja: servicio $168.000 + recargo $15.000 + IVA $34.770 = $217.770; el cupo queda en $8.612.230 y el cargo guarda el origen")
    void criterioPrincipal() {
        servicio.validarCreditoDisponible(cliente, preEmpleo, 5);
        servicio.descontarCredito(cliente, Servicio.builder().idServicio(55).build(), preEmpleo, 5);

        var m = ultimoMovimiento();
        assertThat(m.getMontoBase()).isEqualByComparingTo("168000");
        assertThat(m.getMontoRecargoCiudad()).isEqualByComparingTo("15000");
        assertThat(m.getMontoIvaBase().add(m.getMontoIvaRecargo())).isEqualByComparingTo("34770");
        assertThat(m.getMontoTotal()).isEqualByComparingTo("217770");
        assertThat(m.getOrigenPrecio()).isEqualTo("PLAN_100");
        assertThat(cupo.getCreditoDisponible()).isEqualByComparingTo("8612230");
    }

    @Test
    @DisplayName("El cupo se lee con bloqueo (dos solicitudes simultáneas no superan el cupo)")
    void cupoConBloqueo() {
        servicio.descontarCredito(cliente, Servicio.builder().idServicio(55).build(), preEmpleo, 5);
        verify(pospagoRepo).bloquearPorIdCliente(9);
    }

    @Test
    @DisplayName("Solicitud de $217.770 con cupo de $200.000: no se envía, se explica por qué y no se toca el cupo ni se guarda cargo")
    void cupoInsuficiente() {
        cupo.setCreditoDisponible(new BigDecimal("200000"));
        assertThatThrownBy(() -> servicio.validarCreditoDisponible(cliente, preEmpleo, 5)).isInstanceOf(ApiException.class)
                .hasMessageContaining("No tienes cupo disponible").satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(402));
        assertThatThrownBy(() -> servicio.descontarCredito(cliente, Servicio.builder().idServicio(55).build(), preEmpleo, 5))
                .isInstanceOf(ApiException.class);
        assertThat(cupo.getCreditoDisponible()).isEqualByComparingTo("200000");
        verify(movimientos, never()).save(any());
    }

    @Test
    @DisplayName("Segunda solicitud con el cupo que dejó la primera: la re-validación bajo bloqueo la rechaza aunque la validación previa pasó")
    void doSolicitudesSeguidasNoSuperanElCupo() {
        cupo.setCreditoDisponible(new BigDecimal("300000"));
        servicio.validarCreditoDisponible(cliente, preEmpleo, 5);   // las dos pasaron la validación a la vez
        servicio.validarCreditoDisponible(cliente, preEmpleo, 5);
        servicio.descontarCredito(cliente, Servicio.builder().idServicio(1).build(), preEmpleo, 5);          // la primera descuenta 217.770 → 82.230
        assertThatThrownBy(() -> servicio.descontarCredito(cliente, Servicio.builder().idServicio(2).build(), preEmpleo, 5))
                .isInstanceOf(ApiException.class);
        assertThat(cupo.getCreditoDisponible()).isEqualByComparingTo("82230");
    }

    // ───────────── cliente que pasó de prepago con unidades en su bolsa ─────────────

    @Test
    @DisplayName("Con una unidad de la bolsa: no se descuenta el servicio del cupo; el recargo con IVA sí ($17.850) y el origen es BOLSA")
    void bolsaSoloCobraElRecargo() {
        servicio.validarCreditoDisponible(cliente, preEmpleo, 5, true);
        servicio.descontarCredito(cliente, Servicio.builder().idServicio(55).build(), preEmpleo, 5, true, true, "LOCAL");

        var m = ultimoMovimiento();
        assertThat(m.getMontoBase()).isEqualByComparingTo("0");
        assertThat(m.getMontoRecargoCiudad()).isEqualByComparingTo("15000");
        assertThat(m.getMontoTotal()).isEqualByComparingTo("17850");
        assertThat(m.getOrigenPrecio()).isEqualTo("BOLSA");
        assertThat(cupo.getCreditoDisponible()).isEqualByComparingTo("8812150");
    }

    @Test
    @DisplayName("Unidad de la bolsa y ciudad sede (sin recargo): no baja el cupo ni queda nada por facturar")
    void bolsaSinRecargoNoCobraNada() {
        var bogota = mock(Ciudad.class);
        var ciudades = mock(CiudadRepository.class);
        when(ciudades.findById(1)).thenReturn(Optional.of(bogota));
        when(recargos.calcular(preEmpleo, bogota)).thenReturn(BigDecimal.ZERO);
        var iva = mock(IvaService.class);
        when(iva.porcentaje()).thenReturn(new BigDecimal("19"));
        var s = new ClientePospagoService(pospagoRepo, movimientos, mock(FacturaPospagoRepository.class), mock(PagoWompiRepository.class), ciudades,
                recargos, mock(UsuarioRepository.class), mock(NotificacionRepository.class), iva, precioPospago);

        s.descontarCredito(cliente, Servicio.builder().idServicio(55).build(), preEmpleo, 1, true, true, "LOCAL");

        assertThat(cupo.getCreditoDisponible()).isEqualByComparingTo("8830000");
        verify(movimientos, never()).save(any());
    }

    // ───────────── cancelar ─────────────

    private DevolucionCobroService devolucion(SaldoServicioClienteRepository saldos, LotePrepagoRepository lotes) {
        return new DevolucionCobroService(saldos, lotes, movimientos, pospagoRepo, mock(SaldoServicioClienteService.class), recargos,
                mock(ParametrosTiendaService.class));
    }

    @Test
    @DisplayName("Cancelar en Pendiente: el cupo vuelve a $8.830.000 y el cargo se anula (no sale en la factura)")
    void cancelarDevuelveElCupo() {
        servicio.descontarCredito(cliente, Servicio.builder().idServicio(55).build(), preEmpleo, 5);
        var mov = ultimoMovimiento();
        when(movimientos.findFirstByServicio_IdServicioOrderByFechaGeneradoDesc(55)).thenReturn(Optional.of(mov));
        var s = Servicio.builder().idServicio(55).cliente(cliente).proceso(preEmpleo).build();

        assertThat(devolucion(mock(SaldoServicioClienteRepository.class), mock(LotePrepagoRepository.class)).devolver(s)).isTrue();

        assertThat(cupo.getCreditoDisponible()).isEqualByComparingTo("8830000");
        assertThat(mov.getAnuladoEn()).isNotNull();
    }

    @Test
    @DisplayName("Cancelar una solicitud que usó una unidad de la bolsa (cliente pospago): la unidad vuelve a su lote y el recargo vuelve al cupo")
    void cancelarConBolsa() {
        var saldos = mock(SaldoServicioClienteRepository.class);
        var lotes = mock(LotePrepagoRepository.class);
        when(saldos.devolverUna(anyInt(), anyInt(), any())).thenReturn(1);
        var lote = LotePrepago.builder().idLote(5L).cliente(cliente).proceso(preEmpleo).cobertura("LOCAL").cantidadComprada(10).cantidadConsumida(4)
                .valorUnitario(new BigDecimal("180000")).fechaCompra(LocalDateTime.now().minusDays(10)).fechaVencimiento(LocalDateTime.now().plusMonths(2)).build();
        when(lotes.findById(5L)).thenReturn(Optional.of(lote));
        servicio.descontarCredito(cliente, Servicio.builder().idServicio(55).build(), preEmpleo, 5, true, true, "LOCAL");
        var mov = ultimoMovimiento();
        when(movimientos.findFirstByServicio_IdServicioOrderByFechaGeneradoDesc(55)).thenReturn(Optional.of(mov));
        var s = Servicio.builder().idServicio(55).cliente(cliente).proceso(preEmpleo).idLotePrepago(5L).coberturaConsumo("LOCAL").build();

        assertThat(devolucion(saldos, lotes).devolver(s)).isTrue();

        assertThat(lote.getCantidadConsumida()).isEqualTo(3);                              // la unidad vuelve a su lote
        assertThat(cupo.getCreditoDisponible()).isEqualByComparingTo("8830000");           // el recargo con IVA vuelve al cupo
        assertThat(mov.getAnuladoEn()).isNotNull();
        verify(recargos, never()).reversarPorCancelacion(any(), any(), any());              // el recargo del pospago no es un recargo pendiente prepago
    }
}
