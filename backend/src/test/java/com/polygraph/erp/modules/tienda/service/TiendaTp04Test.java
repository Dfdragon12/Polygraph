package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.TramoPrecioProceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.TramoPrecioProcesoRepository;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.Analisis;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.EntradaBeneficio;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.EntradaPlan;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.EntradaPrecio;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.FilaPlan;
import com.polygraph.erp.modules.tienda.entity.PlanTienda;
import com.polygraph.erp.modules.tienda.repository.BeneficioPlanRepository;
import com.polygraph.erp.modules.tienda.repository.ParametroTiendaAuditoriaRepository;
import com.polygraph.erp.modules.tienda.repository.PlanTiendaRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TP-04: análisis de precios por plan (margen y alertas), guardado validado, beneficios y planes globales. */
class TiendaTp04Test {

    private static PlanTienda plan(int u, String n, boolean destacado, Integer meses) {
        return PlanTienda.builder().unidades(u).nombre(n).visible(true).destacado(destacado).vigenciaMeses(meses).build();
    }

    private static final List<PlanTienda> PLANES = List.of(plan(1, "Plan Inicial", false, 3), plan(5, "Plan Básico", false, 3),
            plan(20, "Plan Medio", true, 6), plan(100, "Plan Empresarial", false, 12), plan(200, "Plan Corporativo", false, null));

    private static EntradaPrecio e(int u, String precio) {
        return new EntradaPrecio(u, precio == null ? null : new BigDecimal(precio));
    }

    private static final List<EntradaPrecio> ANTECEDENTES = List.of(e(1, "40000"), e(5, "38000"), e(20, "35000"), e(100, "32000"), e(200, "30000"));

    private PlanTiendaRepository planRepo;
    private TramoPrecioProcesoRepository tramoRepo;
    private ProcesoRepository procesoRepo;
    private HistorialPreciosService historial;
    private PreciosPlanService servicio;
    private Proceso proceso;

    @BeforeEach
    void setUp() {
        planRepo = mock(PlanTiendaRepository.class);
        when(planRepo.findByVisibleTrueOrderByUnidadesAsc()).thenReturn(PLANES);
        tramoRepo = mock(TramoPrecioProcesoRepository.class);
        procesoRepo = mock(ProcesoRepository.class);
        proceso = Proceso.builder().idProceso(1).nombreProceso("Antecedentes").valor(new BigDecimal("40000")).build();
        when(procesoRepo.findById(1)).thenReturn(Optional.of(proceso));
        var parametros = mock(ParametrosTiendaService.class);
        when(parametros.decimal("margenMinimoPorcentaje")).thenReturn(new BigDecimal("30"));
        historial = mock(HistorialPreciosService.class);
        servicio = new PreciosPlanService(procesoRepo, tramoRepo, planRepo, parametros, historial);
    }

    @Test
    void analisisDeAntecedentesConMargenYDescuentos() {
        Analisis a = servicio.analizar(ANTECEDENTES, new BigDecimal("20000"));

        assertThat(a.valido()).isTrue();
        FilaPlan medio = a.planes().get(2);
        assertThat(medio.precioPaquete()).isEqualByComparingTo("700000");
        assertThat(medio.descuentoVsAnterior()).isEqualByComparingTo("7.9");   // 35.000 contra 38.000
        assertThat(medio.descuentoTotal()).isEqualByComparingTo("12.5");
        assertThat(medio.margenPorcentaje()).isEqualByComparingTo("42.9");     // (35.000 − 20.000) / 35.000
        assertThat(a.descuentoMaximoPorcentaje()).isEqualByComparingTo("25");
        assertThat(a.planes()).allSatisfy(f -> assertThat(f.alertas()).isEmpty());
    }

    @Test
    void alertaDeMargenBajoMenorA30() {
        Analisis a = servicio.analizar(ANTECEDENTES, new BigDecimal("28000"));   // plan de 20: (35.000 − 28.000) / 35.000 = 20 %
        assertThat(a.planes().get(0).alertas()).isEmpty();                        // plan 1: 30 % justo
        assertThat(a.planes().get(2).margenPorcentaje()).isEqualByComparingTo("20");
        assertThat(a.planes().get(2).alertas()).anyMatch(m -> m.contains("Margen bajo"));
        assertThat(a.planes().get(4).alertas()).anyMatch(m -> m.contains("Margen bajo"));
    }

    @Test
    void sinCostoDirectoNoHayMargenNiAlertaDeMargen() {
        Analisis a = servicio.analizar(ANTECEDENTES, null);
        assertThat(a.planes()).allSatisfy(f -> assertThat(f.margenPorcentaje()).isNull());
    }

    @Test
    void planQueNoBajaDePrecioDaAlertaYError() {
        Analisis a = servicio.analizar(List.of(e(1, "40000"), e(5, "38000"), e(20, "35000"), e(100, "35000")), null);
        assertThat(a.valido()).isFalse();
        assertThat(a.errorEscalera()).contains("Plan Empresarial");
        assertThat(a.planes().get(3).alertas()).anyMatch(m -> m.contains("No baja de precio"));
    }

    @Test
    void planSinPrecioSaleVacioParaQueElAdminLoLlene() {
        Analisis a = servicio.analizar(List.of(e(1, "40000"), e(20, "35000")), null);
        assertThat(a.planes().get(1).precioUnitario()).isNull();
        assertThat(a.planes().get(1).descuentoVsAnterior()).isNull();
        assertThat(a.planes().get(2).descuentoVsAnterior()).isEqualByComparingTo("12.5");   // contra el plan de 1
    }

    @Test
    void guardarUnaEscaleraInvalidaFallaYNoGuardaNada() {
        assertThatThrownBy(() -> servicio.guardar(1, List.of(e(1, "40000"), e(5, "38000"), e(20, "35000"), e(100, "35000"))))
                .isInstanceOf(ApiException.class).hasMessageContaining("Plan Empresarial");
        verify(procesoRepo, never()).save(any());
        verify(tramoRepo, never()).save(any());
        verify(historial, never()).registrar(anyInt(), anyString(), any(), any());
        assertThat(proceso.getValor()).isEqualByComparingTo("40000");
    }

    @Test
    void guardarExigeElPrecioDelPlanDeUnaUnidad() {
        assertThatThrownBy(() -> servicio.guardar(1, List.of(e(20, "35000")))).isInstanceOf(ApiException.class);
    }

    @Test
    void guardarActualizaElTramoCreaElNuevoQuitaElVacioYRegistraHistorial() {
        var t20 = TramoPrecioProceso.builder().idTramo(2).proceso(proceso).cantidadMinima(20).valorUnitario(new BigDecimal("35000")).build();
        var t100 = TramoPrecioProceso.builder().idTramo(3).proceso(proceso).cantidadMinima(100).valorUnitario(new BigDecimal("32000")).build();
        var otro = TramoPrecioProceso.builder().idTramo(9).proceso(proceso).cantidadMinima(10).valorUnitario(new BigDecimal("36000")).build();
        when(tramoRepo.findByProceso_IdProcesoOrderByCantidadMinimaAsc(1)).thenReturn(List.of(otro, t20, t100));

        // plan 20 baja a 34.000, el 100 se quita, el de 5 se crea y el 200 sigue sin precio.
        servicio.guardar(1, List.of(e(1, "40000"), e(5, "38000"), e(20, "34000"), e(100, null), e(200, null)));

        assertThat(t20.getValorUnitario()).isEqualByComparingTo("34000");
        verify(tramoRepo).delete(t100);
        verify(tramoRepo, never()).delete(otro);                    // un tramo con otra cantidad no se toca
        verify(tramoRepo).save(org.mockito.ArgumentMatchers.argThat(t -> t.getCantidadMinima() == 5 && t.getValorUnitario().compareTo(new BigDecimal("38000")) == 0));
        verify(historial).registrar(eq(1), eq("Precio plan 20"), eq(new BigDecimal("35000")), eq(new BigDecimal("34000")));
        verify(historial).registrar(eq(1), eq("Precio plan 100"), eq(new BigDecimal("32000")), eq(null));
    }

    // ---------- Beneficios ----------

    @Test
    void beneficiosSeGuardanEnOrdenYValidanElPlan() {
        var repo = mock(BeneficioPlanRepository.class);
        var srv = new BeneficiosPlanService(repo, procesoRepo);
        srv.guardar(1, List.of(new EntradaBeneficio("Soporte por correo", 1), new EntradaBeneficio("  ", 5), new EntradaBeneficio("Gestor dedicado", 20)));
        verify(repo).saveAll(org.mockito.ArgumentMatchers.<List<com.polygraph.erp.modules.tienda.entity.BeneficioPlan>>argThat(l ->
                l.size() == 2 && l.get(0).getOrden() == 0 && l.get(1).getOrden() == 1 && l.get(1).getDesdeUnidades() == 20));

        assertThatThrownBy(() -> srv.guardar(1, List.of(new EntradaBeneficio("X", 7)))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> srv.guardar(1, List.of(new EntradaBeneficio("x".repeat(121), 1)))).isInstanceOf(ApiException.class);
    }

    // ---------- Planes globales ----------

    private static EntradaPlan ep(int u, String nombre, boolean visible, boolean destacado, Integer meses) {
        return new EntradaPlan(u, nombre, visible, destacado, meses);
    }

    @Test
    void planesGlobalesValidanUnSoloDestacadoYVigenciaVaciaEsSinVencimiento() {
        var repo = mock(PlanTiendaRepository.class);
        when(repo.findAll()).thenReturn(new java.util.ArrayList<>(PLANES.stream()
                .map(p -> plan(p.getUnidades(), p.getNombre(), p.getDestacado(), p.getVigenciaMeses())).toList()));
        var auditoria = mock(ParametroTiendaAuditoriaRepository.class);
        var srv = new PlanesAdminService(repo, auditoria, Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> srv.guardar(List.of(ep(1, "A", true, true, 3), ep(5, "B", true, true, 3), ep(20, "C", true, false, 6),
                ep(100, "D", true, false, 12), ep(200, "E", true, false, null)), "admin")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> srv.guardar(List.of(ep(1, "A", false, false, 3), ep(5, "B", false, false, 3), ep(20, "C", false, false, 6),
                ep(100, "D", false, false, 12), ep(200, "E", false, false, null)), "admin")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> srv.guardar(List.of(ep(1, "A", true, false, 3)), "admin")).isInstanceOf(ApiException.class);   // faltan planes

        srv.guardar(List.of(ep(1, "Plan Inicial", true, false, 3), ep(5, "Plan Básico", true, false, 3), ep(20, "Plan Medio", true, false, 6),
                ep(100, "Plan Empresarial", true, true, 12), ep(200, "Plan Corporativo", true, false, null)), "admin");
        // Cambió el destacado (20 → 100): dos registros de auditoría.
        verify(auditoria, org.mockito.Mockito.times(2)).save(any());
    }
}
