package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.tienda.dto.PlanPublicoResponse;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.EntradaPlan;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.EntradaPrecio;
import com.polygraph.erp.shared.enums.ModoPrecioPospago;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** TP-11: lista de precios pospago: plan de 100 o precio propio, validaciones, visibilidad y precio solo para solicitudes nuevas. */
class TiendaTp11Test {

    private PlanesTiendaService planes;
    private ParametrosTiendaService parametros;
    private IvaService iva;
    private ProcesoRepository procesos;
    private PrecioPospagoService servicio;
    private final List<Proceso> catalogo = new ArrayList<>();

    @BeforeEach
    void setUp() {
        planes = mock(PlanesTiendaService.class);
        parametros = mock(ParametrosTiendaService.class);
        iva = mock(IvaService.class);
        procesos = mock(ProcesoRepository.class);
        when(parametros.activo(ParametrosTiendaService.POSPAGO_PLAN_100)).thenReturn(true);
        when(parametros.decimal("margenMinimoPorcentaje")).thenReturn(new BigDecimal("30"));
        when(iva.porcentaje()).thenReturn(new BigDecimal("19"));
        when(procesos.findAll()).thenReturn(catalogo);
        servicio = new PrecioPospagoService(planes, parametros, iva, procesos);
    }

    private PlanPublicoResponse plan(int unidades, String unitario) {
        return new PlanPublicoResponse(unidades, "Plan " + unidades, false, null, new BigDecimal(unitario), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    private Proceso proceso(int id, String nombre, String base, String plan100) {
        Proceso p = Proceso.builder().idProceso(id).nombreProceso(nombre).valor(new BigDecimal(base)).activo(true).aplicaIva(true).build();
        when(planes.planesDe(p)).thenReturn(plan100 == null ? List.of(plan(1, base)) : List.of(plan(1, base), plan(100, plan100)));
        catalogo.add(p);
        return p;
    }

    @Test
    @DisplayName("Antecedentes pospago = $32.000 + IVA (plan de 100); Poligrafía Pre Empleo = $168.000 + IVA")
    void preciosDeLosCriterios() {
        var ant = proceso(1, "Antecedentes", "40000", "32000");
        var pre = proceso(2, "Poligrafía Pre Empleo", "200000", "168000");
        assertThat(servicio.precioVigente(ant)).contains(new BigDecimal("32000"));
        assertThat(servicio.precioVigente(pre)).contains(new BigDecimal("168000"));
        var fila = servicio.lista().stream().filter(f -> f.idProceso() == 1).findFirst().orElseThrow();
        assertThat(fila.precio()).isEqualByComparingTo("32000");
        assertThat(fila.iva()).isEqualByComparingTo("6080");
        assertThat(fila.precioConIva()).isEqualByComparingTo("38080");
        assertThat(fila.origen()).isEqualTo("Auto · plan de 100");
    }

    @Test
    @DisplayName("Si el plan de 100 de Antecedentes pasa a $33.000, las solicitudes nuevas valen $33.000 (el cargo ya creado guarda su monto)")
    void cambiaElPlan100SoloParaLoNuevo() {
        var ant = proceso(1, "Antecedentes", "40000", "32000");
        BigDecimal cobradoAntes = servicio.precioParaCobrar(ant);        // lo que quedó guardado en la solicitud anterior
        when(planes.planesDe(ant)).thenReturn(List.of(plan(1, "40000"), plan(100, "33000")));
        assertThat(servicio.precioParaCobrar(ant)).isEqualByComparingTo("33000");
        assertThat(cobradoAntes).isEqualByComparingTo("32000");
    }

    @Test
    @DisplayName("Modo PROPIO con $50.000: el pospago ve $50.000 aunque el plan de 100 cambie")
    void precioPropio() {
        var p = proceso(3, "Visita", "300000", "250000");
        p.setModoPrecioPospago(ModoPrecioPospago.PROPIO);
        p.setPrecioPospagoPropio(new BigDecimal("50000"));
        assertThat(servicio.precioVigente(p)).contains(new BigDecimal("50000"));
        when(planes.planesDe(p)).thenReturn(List.of(plan(1, "300000"), plan(100, "200000")));
        assertThat(servicio.precioVigente(p)).contains(new BigDecimal("50000"));
        assertThat(servicio.lista().get(0).origen()).isEqualTo("Precio propio");
    }

    @Test
    @DisplayName("Si no se ofrece en pospago no se puede cobrar y no está en la lista del cliente")
    void noOfrecido() {
        var p = proceso(4, "Oculto", "100000", "80000");
        p.setOfrecerPospago(false);
        assertThatThrownBy(() -> servicio.precioParaCobrar(p)).isInstanceOf(ApiException.class).hasMessageContaining("no está disponible");
        var pospago = Cliente.builder().idCliente(1).tipoCliente(TipoCliente.POSPAGO).build();
        assertThat(servicio.listaParaCliente(pospago)).isEmpty();
        assertThat(servicio.lista().get(0).ofrecido()).isFalse();
    }

    @Test
    @DisplayName("Un cliente prepago que pide la lista pospago recibe 403")
    void prepagoRecibe403() {
        proceso(1, "Antecedentes", "40000", "32000");
        var prepago = Cliente.builder().idCliente(2).tipoCliente(TipoCliente.PREPAGO).build();
        assertThatThrownBy(() -> servicio.listaParaCliente(prepago)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
    }

    @Test
    @DisplayName("La lista del cliente pospago no trae origen ni margen y no trae servicios sin precio")
    void listaDelCliente() {
        proceso(1, "Antecedentes", "40000", "32000");
        proceso(2, "Sin plan 100", "50000", null);
        var pospago = Cliente.builder().idCliente(1).tipoCliente(TipoCliente.POSPAGO).build();
        var lista = servicio.listaParaCliente(pospago);
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).servicio()).isEqualTo("Antecedentes");
        assertThat(lista.get(0).precioConIva()).isEqualByComparingTo("38080");
    }

    // ───────────── validaciones al guardar ─────────────

    @Test
    @DisplayName("Ofrecido con precio automático y sin plan de 100: error; con precio propio ≤ 0: error")
    void validaAlGuardar() {
        var sin100 = proceso(1, "Sin plan 100", "50000", null);
        assertThatThrownBy(() -> servicio.validarAlGuardar(sin100, true, ModoPrecioPospago.PLAN_100, null))
                .isInstanceOf(ApiException.class).hasMessageContaining("plan de 100");
        assertThatThrownBy(() -> servicio.validarAlGuardar(sin100, true, ModoPrecioPospago.PROPIO, BigDecimal.ZERO))
                .isInstanceOf(ApiException.class).hasMessageContaining("mayor que 0");
        servicio.validarAlGuardar(sin100, true, ModoPrecioPospago.PROPIO, new BigDecimal("45000"));   // válido
        servicio.validarAlGuardar(sin100, false, ModoPrecioPospago.PLAN_100, null);                   // no se ofrece: no exige precio
    }

    @Test
    @DisplayName("No se puede ocultar el plan de 100 mientras un servicio pospago automático lo use; con precio propio sí")
    void noSeOcultaElPlan100() {
        var ant = proceso(1, "Antecedentes", "40000", "32000");
        var oculta = List.of(new EntradaPlan(100, "Plan Empresarial", false, false, 12));
        assertThatThrownBy(() -> servicio.validarPlanes(oculta)).isInstanceOf(ApiException.class).hasMessageContaining("Antecedentes");
        ant.setModoPrecioPospago(ModoPrecioPospago.PROPIO);
        ant.setPrecioPospagoPropio(new BigDecimal("32000"));
        servicio.validarPlanes(oculta);   // ya no depende del plan de 100
    }

    @Test
    @DisplayName("No se deja sin precio el plan de 100 de un servicio ofrecido en pospago automático")
    void noSeQuitaElPrecio100() {
        var ant = proceso(1, "Antecedentes", "40000", "32000");
        when(procesos.findById(1)).thenReturn(Optional.of(ant));
        var sinPrecio = List.of(new EntradaPrecio(100, null));
        assertThatThrownBy(() -> servicio.validarPrecios(1, sinPrecio)).isInstanceOf(ApiException.class).hasMessageContaining("plan de 100");
        servicio.validarPrecios(1, List.of(new EntradaPrecio(100, new BigDecimal("31000"))));
    }

    @Test
    @DisplayName("Con el interruptor apagado se cobra el precio base de siempre y no se valida nada")
    void interruptorApagado() {
        when(parametros.activo(ParametrosTiendaService.POSPAGO_PLAN_100)).thenReturn(false);
        var p = proceso(1, "Sin plan 100", "50000", null);
        p.setOfrecerPospago(false);
        assertThat(servicio.precioParaCobrar(p)).isEqualByComparingTo("50000");
        servicio.validarAlGuardar(p, true, ModoPrecioPospago.PLAN_100, null);
        servicio.validarPlanes(List.of(new EntradaPlan(100, "x", false, false, null)));
    }

    // ───────────── margen ─────────────

    @Test
    @DisplayName("Margen pospago = (precio − costo) / precio; bajo 30 % se marca en rojo")
    void margen() {
        assertThat(PrecioPospagoService.margen(new BigDecimal("32000"), new BigDecimal("20000"))).isEqualByComparingTo("37.5");
        assertThat(PrecioPospagoService.margen(new BigDecimal("32000"), null)).isNull();
        var p = proceso(1, "Antecedentes", "40000", "32000");
        p.setCostoDirecto(new BigDecimal("25000"));      // 21,9 %
        var fila = servicio.lista().get(0);
        assertThat(fila.margenPorcentaje()).isEqualByComparingTo("21.9");
        assertThat(fila.margenBajo()).isTrue();
    }
}
