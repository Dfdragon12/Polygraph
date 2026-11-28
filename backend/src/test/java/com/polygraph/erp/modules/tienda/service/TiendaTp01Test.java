package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.TramoPrecioProceso;
import com.polygraph.erp.modules.servicios.repository.TramoPrecioProcesoRepository;
import com.polygraph.erp.modules.tienda.dto.PlanPublicoResponse;
import com.polygraph.erp.modules.tienda.entity.BeneficioPlan;
import com.polygraph.erp.modules.tienda.entity.ParametroTienda;
import com.polygraph.erp.modules.tienda.entity.ParametroTiendaAuditoria;
import com.polygraph.erp.modules.tienda.entity.PlanTienda;
import com.polygraph.erp.modules.tienda.repository.BeneficioPlanRepository;
import com.polygraph.erp.modules.tienda.repository.ParametroTiendaAuditoriaRepository;
import com.polygraph.erp.modules.tienda.repository.ParametroTiendaRepository;
import com.polygraph.erp.modules.tienda.repository.PlanTiendaRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TiendaTp01Test {

    private PlanTiendaRepository planes;
    private BeneficioPlanRepository beneficios;
    private TramoPrecioProcesoRepository tramos;
    private PlanesTiendaService servicioPlanes;

    private ParametroTiendaRepository parametros;
    private ParametroTiendaAuditoriaRepository auditoria;
    private ParametrosTiendaService servicioParametros;

    @BeforeEach
    void setUp() {
        planes = mock(PlanTiendaRepository.class);
        beneficios = mock(BeneficioPlanRepository.class);
        tramos = mock(TramoPrecioProcesoRepository.class);
        servicioPlanes = new PlanesTiendaService(planes, beneficios, tramos);

        parametros = mock(ParametroTiendaRepository.class);
        auditoria = mock(ParametroTiendaAuditoriaRepository.class);
        servicioParametros = new ParametrosTiendaService(parametros, auditoria, Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), java.time.ZoneOffset.UTC));
    }

    private static PlanTienda plan(int u, String nombre, boolean destacado, Integer meses) {
        return PlanTienda.builder().unidades(u).nombre(nombre).visible(true).destacado(destacado).vigenciaMeses(meses).build();
    }

    private static TramoPrecioProceso tramo(int min, String valor) {
        return TramoPrecioProceso.builder().cantidadMinima(min).valorUnitario(new BigDecimal(valor)).build();
    }

    private static BeneficioPlan beneficio(String texto, int desde, int orden) {
        return BeneficioPlan.builder().texto(texto).desdeUnidades(desde).orden(orden).build();
    }

    @Test
    void planesAntecedentesConPrecioPaqueteAhorroYBeneficiosAcumulativos() {
        Proceso p = Proceso.builder().idProceso(1).valor(new BigDecimal("40000")).build();
        when(planes.findByVisibleTrueOrderByUnidadesAsc()).thenReturn(List.of(
                plan(1, "Plan Inicial", false, 3), plan(5, "Plan Básico", false, 3), plan(20, "Plan Medio", true, 6),
                plan(100, "Plan Empresarial", false, 12), plan(200, "Plan Corporativo", false, null)));
        // El repositorio los devuelve de mayor a menor cantidad mínima.
        when(tramos.findByProceso_IdProcesoOrderByCantidadMinimaDesc(1)).thenReturn(List.of(
                tramo(200, "30000"), tramo(100, "32000"), tramo(20, "35000"), tramo(5, "38000")));
        when(beneficios.findByProceso_IdProcesoOrderByDesdeUnidadesAscOrdenAsc(1)).thenReturn(List.of(
                beneficio("Soporte por correo", 1, 0), beneficio("Gestor dedicado", 20, 0)));

        List<PlanPublicoResponse> r = servicioPlanes.planesDe(p);

        assertThat(r).extracting(PlanPublicoResponse::unidades).containsExactly(1, 5, 20, 100, 200);
        PlanPublicoResponse inicial = r.get(0);
        assertThat(inicial.precioUnitario()).isEqualByComparingTo("40000");
        assertThat(inicial.precioPaquete()).isEqualByComparingTo("40000");
        assertThat(inicial.ahorro()).isEqualByComparingTo("0");
        assertThat(inicial.beneficios()).containsExactly("Soporte por correo");

        PlanPublicoResponse medio = r.get(2);
        assertThat(medio.destacado()).isTrue();
        assertThat(medio.precioUnitario()).isEqualByComparingTo("35000");
        assertThat(medio.precioPaquete()).isEqualByComparingTo("700000");
        assertThat(medio.ahorro()).isEqualByComparingTo("100000");
        assertThat(medio.vigenciaMeses()).isEqualTo(6);
        assertThat(medio.beneficios()).containsExactly("Soporte por correo", "Gestor dedicado");

        assertThat(r.get(4).vigenciaMeses()).isNull();           // 200 = sin vencimiento
        assertThat(r.get(4).precioPaquete()).isEqualByComparingTo("6000000");
    }

    @Test
    void sinPrecioBaseNoHayPlanes() {
        Proceso p = Proceso.builder().idProceso(2).valor(null).build();
        assertThat(servicioPlanes.planesDe(p)).isEmpty();
        verifyNoInteractions(planes);
    }

    @Test
    void respuestaPublicaDePlanNoTieneCostoDirecto() {
        assertThat(PlanPublicoResponse.class.getRecordComponents())
                .extracting(c -> c.getName().toLowerCase())
                .noneMatch(n -> n.contains("costo"));
        assertThat(com.polygraph.erp.modules.catalogo.dto.ProcesoPublicoResponse.class.getRecordComponents())
                .extracting(c -> c.getName().toLowerCase())
                .noneMatch(n -> n.contains("costo"));
    }

    @Test
    void interruptorSinParametroOValorDistintoDeTrueEstaApagado() {
        when(parametros.findById("cobroConIva")).thenReturn(Optional.empty());
        when(parametros.findById("tiendaPorPlanes")).thenReturn(Optional.of(ParametroTienda.builder().clave("tiendaPorPlanes").valor("false").tipo("BOOLEANO").build()));
        when(parametros.findById("vigenciaPorPlan")).thenReturn(Optional.of(ParametroTienda.builder().clave("vigenciaPorPlan").valor("TRUE").tipo("BOOLEANO").build()));
        assertThat(servicioParametros.activo("cobroConIva")).isFalse();
        assertThat(servicioParametros.activo("tiendaPorPlanes")).isFalse();
        assertThat(servicioParametros.activo("vigenciaPorPlan")).isTrue();
    }

    @Test
    void cambiarUnParametroDejaAuditoriaConHoraDeBogota() {
        ParametroTienda iva = ParametroTienda.builder().clave("ivaPorcentaje").valor("19").tipo("DECIMAL").build();
        when(parametros.findById("ivaPorcentaje")).thenReturn(Optional.of(iva));

        servicioParametros.cambiar("ivaPorcentaje", "16", "admin@polygraph.co");

        ArgumentCaptor<ParametroTiendaAuditoria> cap = ArgumentCaptor.forClass(ParametroTiendaAuditoria.class);
        verify(auditoria).save(cap.capture());
        assertThat(cap.getValue().getValorAnterior()).isEqualTo("19");
        assertThat(cap.getValue().getValorNuevo()).isEqualTo("16");
        assertThat(cap.getValue().getUsuario()).isEqualTo("admin@polygraph.co");
        assertThat(cap.getValue().getFecha().getHour()).isEqualTo(10);   // 15:00 UTC = 10:00 en Bogotá
        assertThat(iva.getValor()).isEqualTo("16");
    }

    @Test
    void cambiarSinCambioRealNoAudita() {
        when(parametros.findById("diasGraciaMora")).thenReturn(Optional.of(ParametroTienda.builder().clave("diasGraciaMora").valor("0").tipo("ENTERO").build()));
        servicioParametros.cambiar("diasGraciaMora", "0", "admin");
        verifyNoInteractions(auditoria);
    }

    @Test
    void valorInvalidoParaElTipoSeRechaza() {
        when(parametros.findById("cobroConIva")).thenReturn(Optional.of(ParametroTienda.builder().clave("cobroConIva").valor("false").tipo("BOOLEANO").build()));
        when(parametros.findById("diasGraciaMora")).thenReturn(Optional.of(ParametroTienda.builder().clave("diasGraciaMora").valor("0").tipo("ENTERO").build()));
        assertThatThrownBy(() -> servicioParametros.cambiar("cobroConIva", "si", "admin")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> servicioParametros.cambiar("diasGraciaMora", "tres", "admin")).isInstanceOf(ApiException.class);
        verifyNoInteractions(auditoria);
    }
}
