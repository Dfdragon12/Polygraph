package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.catalogo.service.DescuentoService;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.pagos.dto.CotizacionOrdenResponse;
import com.polygraph.erp.modules.pagos.dto.CrearOrdenRequest;
import com.polygraph.erp.modules.pagos.dto.ItemCarritoRequest;
import com.polygraph.erp.modules.pagos.dto.OrdenCompraResponse;
import com.polygraph.erp.modules.pagos.entity.MovimientoCreditoPospago;
import com.polygraph.erp.modules.pagos.entity.OrdenCompra;
import com.polygraph.erp.modules.pagos.repository.MovimientoCreditoPospagoRepository;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraItemRepository;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraRepository;
import com.polygraph.erp.modules.pagos.service.ClientePospagoService;
import com.polygraph.erp.modules.pagos.service.OrdenCompraService;
import com.polygraph.erp.modules.pagos.service.RecargoPendienteService;
import com.polygraph.erp.modules.pagos.service.WompiPasarelaPago;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.shared.enums.TipoCliente;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TP-03: IVA por línea, total = subtotal + IVA, y el mismo total hacia la pasarela, el comprobante y el cupo pospago. */
class TiendaTp03Test {

    private static final BigDecimal DIECINUEVE = new BigDecimal("19");

    @Test
    void ivaPorLineaRedondeadoAPesos() {
        assertThat(IvaCalculadora.iva(new BigDecimal("700000"), DIECINUEVE, true)).isEqualByComparingTo("133000");
        assertThat(IvaCalculadora.iva(new BigDecimal("168000"), DIECINUEVE, true)).isEqualByComparingTo("31920");
        assertThat(IvaCalculadora.iva(new BigDecimal("15000"), DIECINUEVE, true)).isEqualByComparingTo("2850");
        assertThat(IvaCalculadora.iva(new BigDecimal("1005"), DIECINUEVE, true)).isEqualByComparingTo("191");   // 190,95
        assertThat(IvaCalculadora.iva(new BigDecimal("1003"), DIECINUEVE, true)).isEqualByComparingTo("191");   // 190,57
        assertThat(IvaCalculadora.iva(new BigDecimal("1001"), DIECINUEVE, true)).isEqualByComparingTo("190");   // 190,19
    }

    @Test
    void sinIvaSiElServicioNoAplicaOElInterruptorEstaApagado() {
        assertThat(IvaCalculadora.iva(new BigDecimal("700000"), DIECINUEVE, false)).isEqualByComparingTo("0");
        assertThat(IvaCalculadora.iva(new BigDecimal("700000"), BigDecimal.ZERO, true)).isEqualByComparingTo("0");
        assertThat(IvaCalculadora.iva(BigDecimal.ZERO, DIECINUEVE, true)).isEqualByComparingTo("0");
    }

    // ---------- Carrito prepago ----------

    private OrdenCompraRepository ordenRepo;
    private Proceso proceso;

    private OrdenCompraService armar(BigDecimal porcentaje, BigDecimal recargos, BigDecimal ivaRecargos, boolean aplicaIva) {
        proceso = Proceso.builder().idProceso(1).nombreProceso("Antecedentes").activo(true).valor(new BigDecimal("40000"))
                .visibleEnTienda(true).aplicaIva(aplicaIva).build();
        ordenRepo = mock(OrdenCompraRepository.class);
        when(ordenRepo.save(any(OrdenCompra.class))).thenAnswer(i -> i.getArgument(0));
        var procesoRepo = mock(ProcesoRepository.class);
        when(procesoRepo.findById(1)).thenReturn(Optional.of(proceso));
        var usuarioRepo = mock(UsuarioRepository.class);
        when(usuarioRepo.findByEmail("c@x.co")).thenReturn(Optional.of(Usuario.builder().idCliente(9).email("c@x.co").build()));
        var clienteRepo = mock(ClienteRepository.class);
        when(clienteRepo.findById(9)).thenReturn(Optional.of(Cliente.builder().idCliente(9).tipoCliente(TipoCliente.PREPAGO).build()));
        var descuentos = mock(DescuentoService.class);
        when(descuentos.mejorPrecioConDescuento(any(), any(), anyInt(), any()))
                .thenAnswer(i -> new DescuentoService.MejorPrecioResult(i.getArgument(1), null));
        var recargoSrv = mock(RecargoPendienteService.class);
        when(recargoSrv.totalPendiente(anyInt())).thenReturn(recargos);
        when(recargoSrv.ivaPendiente(anyInt(), any())).thenReturn(ivaRecargos);

        var parametros = mock(ParametrosTiendaService.class);
        when(parametros.activo(ParametrosTiendaService.TIENDA_POR_PLANES)).thenReturn(true);
        var iva = mock(IvaService.class);
        when(iva.porcentaje()).thenReturn(porcentaje);

        // Planes de Antecedentes de hoy.
        var planes = mock(com.polygraph.erp.modules.tienda.repository.PlanTiendaRepository.class);
        when(planes.findByVisibleTrueOrderByUnidadesAsc()).thenReturn(List.of(
                com.polygraph.erp.modules.tienda.entity.PlanTienda.builder().unidades(1).nombre("Plan Inicial").visible(true).destacado(false).build(),
                com.polygraph.erp.modules.tienda.entity.PlanTienda.builder().unidades(20).nombre("Plan Medio").visible(true).destacado(true).vigenciaMeses(6).build()));
        var beneficios = mock(com.polygraph.erp.modules.tienda.repository.BeneficioPlanRepository.class);
        var tramos = mock(com.polygraph.erp.modules.servicios.repository.TramoPrecioProcesoRepository.class);
        when(tramos.findByProceso_IdProcesoOrderByCantidadMinimaDesc(1)).thenReturn(List.of(
                com.polygraph.erp.modules.servicios.entity.TramoPrecioProceso.builder().cantidadMinima(20).valorUnitario(new BigDecimal("35000")).build()));

        return new OrdenCompraService(ordenRepo, mock(OrdenCompraItemRepository.class),
                mock(com.polygraph.erp.modules.pagos.repository.TransaccionPagoRepository.class),
                mock(com.polygraph.erp.modules.pagos.repository.ComprobanteCompraRepository.class), procesoRepo,
                mock(com.polygraph.erp.modules.servicios.repository.TramoPrecioProcesoRepository.class), clienteRepo,
                mock(CiudadRepository.class), usuarioRepo, mock(WompiPasarelaPago.class),
                mock(com.polygraph.erp.modules.pagos.service.SaldoServicioClienteService.class), descuentos, recargoSrv,
                mock(com.polygraph.erp.modules.notificaciones.service.NotificacionService.class),
                new PlanesTiendaService(planes, beneficios, tramos), parametros, iva);
    }

    /** Con el interruptor pospagoPlan100 apagado el precio pospago es el precio base del servicio (como hasta hoy). */
    private static PrecioPospagoService precioPospagoDelBase() {
        var s = mock(PrecioPospagoService.class);
        when(s.precioParaCobrar(any(Proceso.class))).thenAnswer(i -> ((Proceso) i.getArgument(0)).getValor());
        return s;
    }

    private static CrearOrdenRequest planMedio() {
        return new CrearOrdenRequest(List.of(new ItemCarritoRequest(1, null, 20, null, 1, null)), null);
    }

    @Test
    void unPlanMedioDeAntecedentesSubtotal700Iva133Total833() {
        var servicio = armar(DIECINUEVE, BigDecimal.ZERO, BigDecimal.ZERO, true);

        CotizacionOrdenResponse cot = servicio.cotizar(planMedio(), "c@x.co");
        assertThat(cot.subtotal()).isEqualByComparingTo("700000");
        assertThat(cot.iva()).isEqualByComparingTo("133000");
        assertThat(cot.total()).isEqualByComparingTo("833000");
        assertThat(cot.lineas().get(0).iva()).isEqualByComparingTo("133000");
        assertThat(cot.cobroConIva()).isTrue();

        // La orden guarda lo mismo y su montoTotal es lo que PagoService convierte a centavos para la pasarela.
        OrdenCompraResponse orden = servicio.crearOrden(planMedio(), "c@x.co");
        assertThat(orden.montoTotal()).isEqualByComparingTo("833000");
        assertThat(orden.montoSubtotal()).isEqualByComparingTo("700000");
        assertThat(orden.montoIva()).isEqualByComparingTo("133000");
        assertThat(orden.items().get(0).iva()).isEqualByComparingTo("133000");
        ArgumentCaptor<OrdenCompra> cap = ArgumentCaptor.forClass(OrdenCompra.class);
        verify(ordenRepo).save(cap.capture());
        assertThat(cap.getValue().getMontoTotal().multiply(BigDecimal.valueOf(100)).longValueExact()).isEqualTo(83_300_000L);
    }

    @Test
    void losRecargosPendientesLlevanSuPropioIvaYSuman() {
        var servicio = armar(DIECINUEVE, new BigDecimal("15000"), new BigDecimal("2850"), true);
        CotizacionOrdenResponse cot = servicio.cotizar(planMedio(), "c@x.co");
        assertThat(cot.subtotal()).isEqualByComparingTo("715000");
        assertThat(cot.iva()).isEqualByComparingTo("135850");
        assertThat(cot.total()).isEqualByComparingTo("850850");
        assertThat(cot.recargosIva()).isEqualByComparingTo("2850");
    }

    @Test
    void servicioSinIvaYCobroApagadoNoAgreganNada() {
        var sinIva = armar(DIECINUEVE, BigDecimal.ZERO, BigDecimal.ZERO, false).cotizar(planMedio(), "c@x.co");
        assertThat(sinIva.iva()).isEqualByComparingTo("0");
        assertThat(sinIva.total()).isEqualByComparingTo("700000");

        var apagado = armar(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true).cotizar(planMedio(), "c@x.co");
        assertThat(apagado.cobroConIva()).isFalse();
        assertThat(apagado.total()).isEqualByComparingTo("700000");
    }

    // ---------- Pospago ----------

    @Test
    void pospagoPreEmpleoConRecargoDeTunjaBajaElCupoEnTotalConIva() {
        var pospagoRepo = mock(ClientePospagoRepository.class);
        var cupo = ClientePospago.builder().idCliente(9).limiteCredito(new BigDecimal("1000000")).creditoDisponible(new BigDecimal("1000000")).build();
        when(pospagoRepo.findByIdCliente(9)).thenReturn(Optional.of(cupo));
        when(pospagoRepo.bloquearPorIdCliente(9)).thenReturn(Optional.of(cupo));
        var movimientos = mock(MovimientoCreditoPospagoRepository.class);
        var ciudades = mock(CiudadRepository.class);
        Ciudad tunja = mock(Ciudad.class);
        when(ciudades.findById(5)).thenReturn(Optional.of(tunja));
        var recargos = mock(RecargoPendienteService.class);
        Proceso preEmpleo = Proceso.builder().idProceso(7).nombreProceso("Poligrafía Pre Empleo").valor(new BigDecimal("168000")).aplicaIva(true).build();
        when(recargos.calcular(preEmpleo, tunja)).thenReturn(new BigDecimal("15000"));
        var iva = mock(IvaService.class);
        when(iva.porcentaje()).thenReturn(DIECINUEVE);

        var servicio = new ClientePospagoService(pospagoRepo, movimientos,
                mock(com.polygraph.erp.modules.pagos.repository.FacturaPospagoRepository.class),
                mock(com.polygraph.erp.modules.pagos.repository.PagoWompiRepository.class), ciudades, recargos,
                mock(UsuarioRepository.class), mock(com.polygraph.erp.shared.repository.NotificacionRepository.class), iva, precioPospagoDelBase());

        servicio.descontarCredito(Cliente.builder().idCliente(9).build(), Servicio.builder().idServicio(55).build(), preEmpleo, 5);

        ArgumentCaptor<MovimientoCreditoPospago> cap = ArgumentCaptor.forClass(MovimientoCreditoPospago.class);
        verify(movimientos).save(cap.capture());
        MovimientoCreditoPospago m = cap.getValue();
        assertThat(m.getMontoIvaBase()).isEqualByComparingTo("31920");
        assertThat(m.getMontoIvaRecargo()).isEqualByComparingTo("2850");
        assertThat(m.getMontoTotal()).isEqualByComparingTo("217770");
        assertThat(cupo.getCreditoDisponible()).isEqualByComparingTo("782230");   // 1.000.000 − 217.770
    }
}
