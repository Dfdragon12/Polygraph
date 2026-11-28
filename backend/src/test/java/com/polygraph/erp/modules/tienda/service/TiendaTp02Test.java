package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.service.DescuentoService;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.pagos.dto.CrearOrdenRequest;
import com.polygraph.erp.modules.pagos.dto.ItemCarritoRequest;
import com.polygraph.erp.modules.pagos.entity.OrdenCompra;
import com.polygraph.erp.modules.pagos.entity.OrdenCompraItem;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraItemRepository;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraRepository;
import com.polygraph.erp.modules.pagos.service.OrdenCompraService;
import com.polygraph.erp.modules.pagos.service.RecargoPendienteService;
import com.polygraph.erp.modules.pagos.service.WompiPasarelaPago;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.tienda.dto.PlanPublicoResponse;
import com.polygraph.erp.modules.tienda.entity.PlanTienda;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TP-02: precio fijo por plan, escalera estrictamente decreciente y compra recalculada por el backend. */
class TiendaTp02Test {

    private static PlanTienda plan(int u, String nombre) {
        return PlanTienda.builder().unidades(u).nombre(nombre).visible(true).destacado(false).build();
    }

    private static final List<PlanTienda> PLANES = List.of(plan(1, "Plan Inicial"), plan(5, "Plan Básico"), plan(20, "Plan Medio"),
            plan(100, "Plan Empresarial"), plan(200, "Plan Corporativo"));

    private static Map<Integer, BigDecimal> antecedentes() {
        return Map.of(5, new BigDecimal("38000"), 20, new BigDecimal("35000"), 100, new BigDecimal("32000"), 200, new BigDecimal("30000"));
    }

    // ---------- Calculadora pura ----------

    @Test
    void escaleraDeAntecedentesEsValida() {
        assertThat(CalculadoraPlanes.validarEscalera(PLANES, new BigDecimal("40000"), antecedentes())).isEmpty();
        assertThat(CalculadoraPlanes.conPrecio(PLANES, new BigDecimal("40000"), antecedentes()))
                .extracting(p -> p.precioUnitario().intValue()).containsExactly(40000, 38000, 35000, 32000, 30000);
    }

    @Test
    void planDe100MasCaroOIgualQueElDe20SeRechazaConMensaje() {
        var igual = Map.of(5, new BigDecimal("38000"), 20, new BigDecimal("35000"), 100, new BigDecimal("35000"));
        var mayor = Map.of(5, new BigDecimal("38000"), 20, new BigDecimal("35000"), 100, new BigDecimal("36000"));
        assertThat(CalculadoraPlanes.validarEscalera(PLANES, new BigDecimal("40000"), igual))
                .hasValueSatisfying(m -> assertThat(m).contains("Plan Empresarial").contains("Plan Medio").contains("35.000"));
        assertThat(CalculadoraPlanes.validarEscalera(PLANES, new BigDecimal("40000"), mayor)).isPresent();
    }

    @Test
    void siFaltaElPlanDe5ElDe20SeComparaConElDe1YElDe5NoSeMuestra() {
        var sinCinco = Map.of(20, new BigDecimal("35000"));
        assertThat(CalculadoraPlanes.conPrecio(PLANES, new BigDecimal("40000"), sinCinco))
                .extracting(p -> p.plan().getUnidades()).containsExactly(1, 20);
        assertThat(CalculadoraPlanes.validarEscalera(PLANES, new BigDecimal("40000"), sinCinco)).isEmpty();
        assertThat(CalculadoraPlanes.validarEscalera(PLANES, new BigDecimal("40000"), Map.of(20, new BigDecimal("40000")))).isPresent();
    }

    @Test
    void descuentoPorcentajeConUnDecimal() {
        var base = new BigDecimal("40000");
        assertThat(CalculadoraPlanes.descuentoPorcentaje(new BigDecimal("40000"), base)).isEqualByComparingTo("0");
        assertThat(CalculadoraPlanes.descuentoPorcentaje(new BigDecimal("38000"), base)).isEqualByComparingTo("5");
        assertThat(CalculadoraPlanes.descuentoPorcentaje(new BigDecimal("35000"), base)).isEqualByComparingTo("12.5");
        assertThat(CalculadoraPlanes.descuentoPorcentaje(new BigDecimal("30000"), base)).isEqualByComparingTo("25");
    }

    // ---------- PlanesTiendaService con los precios de hoy de Antecedentes ----------

    private PlanesTiendaService planesAntecedentes(Proceso p) {
        var planes = mock(com.polygraph.erp.modules.tienda.repository.PlanTiendaRepository.class);
        var beneficios = mock(com.polygraph.erp.modules.tienda.repository.BeneficioPlanRepository.class);
        var tramos = mock(com.polygraph.erp.modules.servicios.repository.TramoPrecioProcesoRepository.class);
        when(planes.findByVisibleTrueOrderByUnidadesAsc()).thenReturn(PLANES);
        when(beneficios.findByProceso_IdProcesoOrderByDesdeUnidadesAscOrdenAsc(p.getIdProceso())).thenReturn(List.of());
        when(tramos.findByProceso_IdProcesoOrderByCantidadMinimaDesc(p.getIdProceso())).thenReturn(antecedentes().entrySet().stream()
                .map(e -> com.polygraph.erp.modules.servicios.entity.TramoPrecioProceso.builder().cantidadMinima(e.getKey()).valorUnitario(e.getValue()).build())
                .toList());
        return new PlanesTiendaService(planes, beneficios, tramos);
    }

    @Test
    void criteriosDeAceptacionDeAntecedentes() {
        Proceso p = Proceso.builder().idProceso(1).nombreProceso("Antecedentes").valor(new BigDecimal("40000")).build();
        var servicio = planesAntecedentes(p);
        List<PlanPublicoResponse> r = servicio.planesDe(p);

        assertThat(r.get(0).precioPaquete()).isEqualByComparingTo("40000");
        assertThat(r.get(1).precioPaquete()).isEqualByComparingTo("190000");
        assertThat(r.get(1).ahorro()).isEqualByComparingTo("10000");
        assertThat(r.get(2).precioPaquete()).isEqualByComparingTo("700000");
        assertThat(r.get(2).ahorro()).isEqualByComparingTo("100000");
        assertThat(r.get(3).precioPaquete()).isEqualByComparingTo("3200000");
        assertThat(r.get(4).precioPaquete()).isEqualByComparingTo("6000000");
        assertThat(servicio.descuentoMaximo(r)).isEqualByComparingTo("25");
        assertThatThrownBy(() -> servicio.planDe(p, 7)).isInstanceOf(ApiException.class);
    }

    // ---------- Compra: el backend recalcula y guarda plan, cobertura y precio aplicado ----------

    private OrdenCompraService ordenes;
    private ParametrosTiendaService parametros;
    private OrdenCompraItemRepository itemRepo;
    private Proceso proceso;

    @BeforeEach
    void armarCompra() {
        proceso = Proceso.builder().idProceso(1).nombreProceso("Antecedentes").activo(true).valor(new BigDecimal("40000")).visibleEnTienda(true).build();

        var ordenRepo = mock(OrdenCompraRepository.class);
        when(ordenRepo.save(any(OrdenCompra.class))).thenAnswer(i -> i.getArgument(0));
        itemRepo = mock(OrdenCompraItemRepository.class);
        var procesoRepo = mock(ProcesoRepository.class);
        when(procesoRepo.findById(1)).thenReturn(Optional.of(proceso));
        var usuarioRepo = mock(UsuarioRepository.class);
        when(usuarioRepo.findByEmail("c@x.co")).thenReturn(Optional.of(Usuario.builder().idCliente(9).email("c@x.co").build()));
        var clienteRepo = mock(ClienteRepository.class);
        when(clienteRepo.findById(9)).thenReturn(Optional.of(Cliente.builder().idCliente(9).tipoCliente(TipoCliente.PREPAGO).build()));
        var descuentos = mock(DescuentoService.class);
        when(descuentos.mejorPrecioConDescuento(any(), any(), anyInt(), any()))
                .thenAnswer(i -> new DescuentoService.MejorPrecioResult(i.getArgument(1), null));
        var recargos = mock(RecargoPendienteService.class);
        when(recargos.totalPendiente(anyInt())).thenReturn(BigDecimal.ZERO);
        when(recargos.ivaPendiente(anyInt(), any())).thenReturn(BigDecimal.ZERO);
        var pasarela = mock(WompiPasarelaPago.class);

        parametros = mock(ParametrosTiendaService.class);
        var iva = mock(IvaService.class);
        when(iva.porcentaje()).thenReturn(BigDecimal.ZERO);
        when(parametros.activo(ParametrosTiendaService.TIENDA_POR_PLANES)).thenReturn(true);

        ordenes = new OrdenCompraService(ordenRepo, itemRepo, mock(com.polygraph.erp.modules.pagos.repository.TransaccionPagoRepository.class),
                mock(com.polygraph.erp.modules.pagos.repository.ComprobanteCompraRepository.class), procesoRepo,
                mock(com.polygraph.erp.modules.servicios.repository.TramoPrecioProcesoRepository.class), clienteRepo,
                mock(com.polygraph.erp.modules.catalogo.repository.CiudadRepository.class), usuarioRepo, pasarela,
                mock(com.polygraph.erp.modules.pagos.service.SaldoServicioClienteService.class), descuentos, recargos,
                mock(com.polygraph.erp.modules.notificaciones.service.NotificacionService.class),
                planesAntecedentes(proceso), parametros, iva);
    }

    @SuppressWarnings("unchecked")
    @Test
    void compraPorPlanesIgnoraElPrecioDelNavegadorYGuardaElSnapshot() {
        // 7 unidades = 1 Plan Básico (5) + 2 Plan Inicial (1); el navegador mandó un precio viejo y falso.
        var req = new CrearOrdenRequest(List.of(
                new ItemCarritoRequest(1, null, 5, null, 1, new BigDecimal("1")),
                new ItemCarritoRequest(1, null, 1, "local", 2, null)), null);

        var resp = ordenes.crearOrden(req, "c@x.co");

        ArgumentCaptor<List<OrdenCompraItem>> cap = ArgumentCaptor.forClass(List.class);
        verify(itemRepo).saveAll(cap.capture());
        OrdenCompraItem basico = cap.getValue().get(0);
        assertThat(basico.getCantidad()).isEqualTo(5);
        assertThat(basico.getUnidadesPlan()).isEqualTo(5);
        assertThat(basico.getCantidadPaquetes()).isEqualTo(1);
        assertThat(basico.getCobertura()).isEqualTo("LOCAL");
        assertThat(basico.getValorUnitario()).isEqualByComparingTo("38000");
        assertThat(basico.getSubtotal()).isEqualByComparingTo("190000");
        OrdenCompraItem inicial = cap.getValue().get(1);
        assertThat(inicial.getCantidad()).isEqualTo(2);
        assertThat(inicial.getSubtotal()).isEqualByComparingTo("80000");
        assertThat(resp.montoTotal()).isEqualByComparingTo("270000");
    }

    @Test
    void conPlanesUnPlanSinPrecioOUnaCoberturaDistintaSeRechazan() {
        assertThatThrownBy(() -> ordenes.crearOrden(new CrearOrdenRequest(List.of(new ItemCarritoRequest(1, null, 7, null, 1, null)), null), "c@x.co"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ordenes.crearOrden(new CrearOrdenRequest(List.of(new ItemCarritoRequest(1, null, 5, "NACIONAL", 1, null)), null), "c@x.co"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ordenes.crearOrden(new CrearOrdenRequest(List.of(new ItemCarritoRequest(1, 3, null, null, null, null)), null), "c@x.co"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void conElInterruptorApagadoSigueLaCantidadLibreConTramos() {
        when(parametros.activo(ParametrosTiendaService.TIENDA_POR_PLANES)).thenReturn(false);
        var resp = ordenes.crearOrden(new CrearOrdenRequest(List.of(new ItemCarritoRequest(1, 3, null, null, null, null)), null), "c@x.co");
        // Sin tramos configurados en este mock, 3 unidades valen el precio base.
        assertThat(resp.montoTotal()).isEqualByComparingTo("120000");
        assertThat(resp.items().get(0).unidadesPlan()).isNull();
    }
}
