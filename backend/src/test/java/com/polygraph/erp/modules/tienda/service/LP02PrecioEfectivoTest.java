package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.tienda.dto.PlanPublicoResponse;
import com.polygraph.erp.shared.enums.ModoPrecioPospago;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** LP-02: un solo precio efectivo por servicio (Automático = plan de 100, Manual = precio propio, sin ninguno = null) y la forma de la fila de la lista. */
@DisplayName("LP-02 — precio efectivo pospago")
class LP02PrecioEfectivoTest {

    private PlanesTiendaService planes;
    private ProcesoRepository procesos;
    private PrecioPospagoService servicio;

    @BeforeEach
    void setUp() {
        planes = mock(PlanesTiendaService.class);
        var parametros = mock(ParametrosTiendaService.class);
        var iva = mock(IvaService.class);
        procesos = mock(ProcesoRepository.class);
        when(parametros.activo(ParametrosTiendaService.POSPAGO_PLAN_100)).thenReturn(true);
        when(parametros.decimal("margenMinimoPorcentaje")).thenReturn(new BigDecimal("30"));
        when(iva.porcentaje()).thenReturn(new BigDecimal("19"));
        servicio = new PrecioPospagoService(planes, parametros, iva, procesos, mock(UsuarioRepository.class));
    }

    private PlanPublicoResponse plan(int unidades, String unitario) {
        return new PlanPublicoResponse(unidades, "Plan " + unidades, false, null, new BigDecimal(unitario), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    private Proceso proceso(Integer plan100) {
        Proceso p = Proceso.builder().idProceso(1).nombreProceso("Poligrafía Pre Empleo").valor(new BigDecimal("200000")).activo(true).aplicaIva(true).build();
        when(planes.planesDe(p)).thenReturn(plan100 == null ? List.of(plan(1, "200000")) : List.of(plan(1, "200000"), plan(100, plan100.toString())));
        when(procesos.findById(1)).thenReturn(Optional.of(p));
        when(procesos.findAll()).thenReturn(List.of(p));
        return p;
    }

    @Test
    @DisplayName("AUTO con plan de 100: el precio es el del plan")
    void autoConPlan() {
        var p = proceso(168000);
        assertThat(servicio.precioEfectivo(p)).isEqualByComparingTo("168000");
        assertThat(servicio.precioEfectivo(1)).isEqualByComparingTo("168000");
    }

    @Test
    @DisplayName("AUTO sin plan de 100: sin precio (null) y no es visible para el cliente")
    void autoSinPlan() {
        var p = proceso(null);
        assertThat(servicio.precioEfectivo(p)).isNull();
        var fila = servicio.lista().get(0);
        assertThat(fila.precio()).isNull();
        assertThat(fila.precioPlan100()).isNull();
        assertThat(fila.visibleParaCliente()).isFalse();
    }

    @Test
    @DisplayName("MANUAL: el precio es el manual")
    void manual() {
        var p = proceso(168000);
        p.setModoPrecioPospago(ModoPrecioPospago.PROPIO);
        p.setPrecioPospagoPropio(new BigDecimal("160000"));
        assertThat(servicio.precioEfectivo(p)).isEqualByComparingTo("160000");
        var fila = servicio.lista().get(0);
        assertThat(fila.modo()).isEqualTo("MANUAL");
        assertThat(fila.precioManual()).isEqualByComparingTo("160000");
        assertThat(fila.precioPlan100()).isEqualByComparingTo("168000");
        assertThat(fila.iva()).isEqualByComparingTo("30400");
        assertThat(fila.visibleParaCliente()).isTrue();
    }

    @Test
    @DisplayName("MANUAL cuando cambia el plan de 100: el precio manual no se mueve")
    void manualNoSigueAlPlan() {
        var p = proceso(168000);
        p.setModoPrecioPospago(ModoPrecioPospago.PROPIO);
        p.setPrecioPospagoPropio(new BigDecimal("160000"));
        when(planes.planesDe(p)).thenReturn(List.of(plan(1, "200000"), plan(100, "150000")));
        assertThat(servicio.precioEfectivo(p)).isEqualByComparingTo("160000");
    }

    @Test
    @DisplayName("AUTO cuando cambia el plan de 100: el precio sigue al plan")
    void autoSigueAlPlan() {
        var p = proceso(168000);
        when(planes.planesDe(p)).thenReturn(List.of(plan(1, "200000"), plan(100, "150000")));
        assertThat(servicio.precioEfectivo(p)).isEqualByComparingTo("150000");
    }

    @Test
    @DisplayName("No ofrecido: no es visible para el cliente aunque tenga precio; el margen se calcula sobre el precio efectivo")
    void noOfrecidoYMargen() {
        var p = proceso(168000);
        p.setCostoDirecto(new BigDecimal("140000"));   // (168000-140000)/168000 = 16,7 %
        p.setOfrecerPospago(false);
        var fila = servicio.lista().get(0);
        assertThat(fila.ofrecido()).isFalse();
        assertThat(fila.visibleParaCliente()).isFalse();
        assertThat(fila.margenPorcentaje()).isEqualByComparingTo("16.7");
        assertThat(fila.margenBajo()).isTrue();
    }
}
