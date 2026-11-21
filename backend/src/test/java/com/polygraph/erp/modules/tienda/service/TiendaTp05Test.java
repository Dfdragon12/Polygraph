package com.polygraph.erp.modules.tienda.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polygraph.erp.modules.servicios.entity.PrecioCiudadProceso;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.TramoPrecioProceso;
import com.polygraph.erp.modules.servicios.repository.PrecioCiudadProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.TramoPrecioProcesoRepository;
import com.polygraph.erp.modules.tienda.entity.ImportacionCatalogo;
import com.polygraph.erp.modules.tienda.entity.PlanTienda;
import com.polygraph.erp.modules.tienda.repository.ImportacionCatalogoRepository;
import com.polygraph.erp.modules.tienda.repository.PlanTiendaRepository;
import com.polygraph.erp.modules.tienda.service.ImportacionCatalogoService.Resultado;
import com.polygraph.erp.modules.tienda.service.ImportacionCatalogoService.ServicioResultado;
import com.polygraph.erp.shared.enums.NivelCiudad;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TP-05: importación de precios y textos: simulación sin guardar, todo o nada, solo servicios existentes y campos ausentes intactos. */
class TiendaTp05Test {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static PlanTienda plan(int u, String n) {
        return PlanTienda.builder().unidades(u).nombre(n).visible(true).destacado(false).build();
    }

    private ProcesoRepository procesoRepo;
    private TramoPrecioProcesoRepository tramoRepo;
    private PrecioCiudadProcesoRepository ciudadRepo;
    private PreciosPlanService precios;
    private BeneficiosPlanService beneficios;
    private ImportacionCatalogoRepository registro;
    private ImportacionCatalogoService servicio;
    private Proceso antecedentes;

    @BeforeEach
    void setUp() {
        procesoRepo = mock(ProcesoRepository.class);
        tramoRepo = mock(TramoPrecioProcesoRepository.class);
        ciudadRepo = mock(PrecioCiudadProcesoRepository.class);
        var planes = mock(PlanTiendaRepository.class);
        when(planes.findByVisibleTrueOrderByUnidadesAsc()).thenReturn(List.of(plan(1, "Plan Inicial"), plan(5, "Plan Básico"),
                plan(20, "Plan Medio"), plan(100, "Plan Empresarial"), plan(200, "Plan Corporativo")));
        precios = mock(PreciosPlanService.class);
        beneficios = mock(BeneficiosPlanService.class);
        var parametros = mock(ParametrosTiendaService.class);
        when(parametros.decimal("margenMinimoPorcentaje")).thenReturn(new BigDecimal("30"));
        registro = mock(ImportacionCatalogoRepository.class);
        servicio = new ImportacionCatalogoService(procesoRepo, tramoRepo, ciudadRepo, planes, precios, beneficios, parametros,
                mock(HistorialPreciosService.class), registro, Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneOffset.UTC));

        antecedentes = Proceso.builder().idProceso(1).nombreProceso("Antecedentes").valor(new BigDecimal("38000"))
                .diasHabilesEntrega(2).aplicaPrecioCiudad(false).tituloComercial("Antiguo").build();
        when(procesoRepo.findById(1)).thenReturn(Optional.of(antecedentes));
        when(tramoRepo.findByProceso_IdProcesoOrderByCantidadMinimaAsc(1)).thenReturn(List.of(
                TramoPrecioProceso.builder().idTramo(1).proceso(antecedentes).cantidadMinima(20).valorUnitario(new BigDecimal("33000")).build()));
        when(ciudadRepo.findByProceso_IdProcesoAndNivelCiudad(anyInt(), any())).thenReturn(Optional.empty());
        when(beneficios.listar(1)).thenReturn(List.of());
    }

    private static JsonNode json(String s) {
        try { return JSON.readTree(s); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static final String ANTECEDENTES = """
            {"servicios":[{"idProceso":1,"nombreProceso":"Antecedentes","tituloComercial":"Consulta de antecedentes",
              "incluye":["Registraduría","Listas ONU"],"unidadSingular":"consulta","unidadPlural":"consultas","costoDirecto":14500,
              "diasHabilesEntrega":1,"visibleEnTienda":true,"aplicaIva":true,
              "precios":{"1":40000,"5":38000,"20":35000,"100":32000,"200":30000},
              "recargoIntermedia":0,"recargoMunicipio":0,"ofrecerPospago":true,"precioPospagoPropio":null,
              "beneficios":[{"texto":"Seguimiento en línea del estado","desde":1}]}]}""";

    @Test
    void simulacionMuestraAntesYDespuesYNoGuardaNada() {
        Resultado r = servicio.importar(json(ANTECEDENTES), false, "admin");

        assertThat(r.modo()).isEqualTo("simulacion");
        assertThat(r.aplicado()).isFalse();
        assertThat(r.serviciosConErrores()).isZero();
        ServicioResultado s = r.servicios().get(0);
        assertThat(s.cambios()).anySatisfy(c -> {
            assertThat(c.campo()).isEqualTo("Precio plan 1"); assertThat(c.antes()).isEqualTo("38000"); assertThat(c.despues()).isEqualTo("40000");
        });
        assertThat(s.cambios()).anySatisfy(c -> {
            assertThat(c.campo()).isEqualTo("Precio plan 20"); assertThat(c.antes()).isEqualTo("33000"); assertThat(c.despues()).isEqualTo("35000");
        });
        assertThat(s.cambios()).anySatisfy(c -> assertThat(c.campo()).isEqualTo("tituloComercial"));
        // Los recargos en 0 sobre «sin recargo» y precioPospagoPropio null sobre null no son cambios.
        assertThat(s.cambios()).noneMatch(c -> c.campo().startsWith("recargo") || c.campo().equals("precioPospagoPropio"));

        verify(procesoRepo, never()).save(any());
        verify(precios, never()).guardar(anyInt(), any());
        verify(registro, never()).save(any());
        assertThat(antecedentes.getTituloComercial()).isEqualTo("Antiguo");   // nada cambió en la entidad
    }

    @Test
    void aplicarGuardaTodoYRegistraQuienImportoYCuando() {
        Resultado r = servicio.importar(json(ANTECEDENTES), true, "carlos@polygraph.co");

        assertThat(r.aplicado()).isTrue();
        assertThat(antecedentes.getTituloComercial()).isEqualTo("Consulta de antecedentes");
        assertThat(antecedentes.getIncluye()).isEqualTo("Registraduría\nListas ONU");
        assertThat(antecedentes.getCostoDirecto()).isEqualByComparingTo("14500");
        assertThat(antecedentes.getDiasHabilesEntrega()).isEqualTo(1);
        verify(precios).guardar(anyInt(), any());
        verify(beneficios).guardar(anyInt(), any());
        ArgumentCaptor<ImportacionCatalogo> cap = ArgumentCaptor.forClass(ImportacionCatalogo.class);
        verify(registro).save(cap.capture());
        assertThat(cap.getValue().getUsuario()).isEqualTo("carlos@polygraph.co");
        assertThat(cap.getValue().getFecha().getHour()).isEqualTo(10);          // 15:00 UTC = 10:00 Bogotá
        assertThat(cap.getValue().getIdsProceso()).isEqualTo("1");
    }

    @Test
    void siUnServicioFallaNoSeImportaNingunoYSeDiceCualYPorQue() {
        var otro = Proceso.builder().idProceso(2).nombreProceso("Visita").valor(new BigDecimal("100000")).build();
        when(procesoRepo.findById(2)).thenReturn(Optional.of(otro));
        when(tramoRepo.findByProceso_IdProcesoOrderByCantidadMinimaAsc(2)).thenReturn(List.of());
        String body = """
                {"servicios":[
                  {"idProceso":1,"tituloComercial":"Nuevo título"},
                  {"idProceso":2,"precios":{"1":100000,"5":90000,"20":95000}}]}""";

        Resultado r = servicio.importar(json(body), true, "admin");

        assertThat(r.aplicado()).isFalse();
        assertThat(r.serviciosConErrores()).isEqualTo(1);
        assertThat(r.servicios().get(1).errores()).anyMatch(m -> m.contains("Plan Medio") && m.contains("Plan Básico"));
        verify(procesoRepo, never()).save(any());
        verify(registro, never()).save(any());
        assertThat(antecedentes.getTituloComercial()).isEqualTo("Antiguo");   // el servicio válido tampoco se importó
    }

    @Test
    void unIdQueNoExisteFallaYNuncaCreaServicios() {
        Resultado r = servicio.importar(json("{\"servicios\":[{\"idProceso\":99,\"nombreProceso\":\"Nuevo\"}]}"), true, "admin");
        assertThat(r.aplicado()).isFalse();
        assertThat(r.servicios().get(0).errores()).anyMatch(m -> m.contains("No existe") && m.contains("99"));
        verify(procesoRepo, never()).save(any());

        Resultado sinId = servicio.importar(json("{\"servicios\":[{\"nombreProceso\":\"Antecedentes\"}]}"), false, "admin");
        assertThat(sinId.servicios().get(0).errores()).anyMatch(m -> m.contains("idProceso"));
    }

    @Test
    void nombreDistintoEsSoloUnaAdvertencia() {
        Resultado r = servicio.importar(json("{\"servicios\":[{\"idProceso\":1,\"nombreProceso\":\"Poligrafía\",\"tituloComercial\":\"X\"}]}"), false, "admin");
        assertThat(r.serviciosConErrores()).isZero();
        assertThat(r.servicios().get(0).advertencias()).anyMatch(m -> m.contains("no coincide"));
        // Con tildes y mayúsculas distintas sí coincide.
        Resultado igual = servicio.importar(json("{\"servicios\":[{\"idProceso\":1,\"nombreProceso\":\"  ANTECEDENTES \",\"tituloComercial\":\"X\"}]}"), false, "admin");
        assertThat(igual.servicios().get(0).advertencias()).noneMatch(m -> m.contains("no coincide"));
    }

    @Test
    void loQueNoVieneNoSeTocaYLoQueVieneIgualNoEsCambio() {
        Resultado r = servicio.importar(json("{\"servicios\":[{\"idProceso\":1}]}"), true, "admin");
        assertThat(r.aplicado()).isTrue();
        assertThat(r.servicios().get(0).cambios()).isEmpty();
        verify(precios, never()).guardar(anyInt(), any());
        assertThat(antecedentes.getTituloComercial()).isEqualTo("Antiguo");
        assertThat(antecedentes.getValor()).isEqualByComparingTo("38000");
    }

    @Test
    void textosLargosYListasExcedidasSeRechazan() {
        String largo = "x".repeat(121);
        Resultado r = servicio.importar(json("{\"servicios\":[{\"idProceso\":1,\"tituloComercial\":\"" + largo + "\","
                + "\"incluye\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\"],\"beneficios\":[{\"texto\":\"a\",\"desde\":7}]}]}"), false, "admin");
        List<String> e = r.servicios().get(0).errores();
        assertThat(e).anyMatch(m -> m.contains("tituloComercial"));
        assertThat(e).anyMatch(m -> m.contains("incluye"));
        assertThat(e).anyMatch(m -> m.contains("desde"));
    }

    @Test
    void ofrecerPospagoYPrecioPropioSeGuardanYAdvierteSiNoHayPrecioPospago() {
        String body = """
                {"servicios":[{"idProceso":1,"ofrecerPospago":false,"precioPospagoPropio":31000}]}""";
        Resultado r = servicio.importar(json(body), true, "admin");
        assertThat(r.aplicado()).isTrue();
        assertThat(antecedentes.getOfrecerPospago()).isFalse();
        assertThat(antecedentes.getPrecioPospagoPropio()).isEqualByComparingTo("31000");

        // Ofrecido, sin plan de 100 y sin precio propio → advertencia (no error).
        antecedentes.setPrecioPospagoPropio(null);
        Resultado sin = servicio.importar(json("{\"servicios\":[{\"idProceso\":1,\"ofrecerPospago\":true}]}"), false, "admin");
        assertThat(sin.servicios().get(0).advertencias()).anyMatch(m -> m.contains("sin precio pospago"));
        // Precio propio en cero o negativo: error.
        Resultado malo = servicio.importar(json("{\"servicios\":[{\"idProceso\":1,\"precioPospagoPropio\":0}]}"), false, "admin");
        assertThat(malo.servicios().get(0).errores()).anyMatch(m -> m.contains("precioPospagoPropio"));
    }

    @Test
    void recargosSeGuardanPorNivelYDeducenSiAplicaPrecioCiudad() {
        Resultado r = servicio.importar(json("{\"servicios\":[{\"idProceso\":1,\"recargoIntermedia\":15000,\"recargoMunicipio\":0}]}"), false, "admin");
        assertThat(r.servicios().get(0).cambios()).anySatisfy(c -> {
            assertThat(c.campo()).isEqualTo("aplicaPrecioCiudad"); assertThat(c.despues()).isEqualTo("true");
        });

        servicio.importar(json("{\"servicios\":[{\"idProceso\":1,\"recargoIntermedia\":15000}]}"), true, "admin");
        ArgumentCaptor<PrecioCiudadProceso> cap = ArgumentCaptor.forClass(PrecioCiudadProceso.class);
        verify(ciudadRepo).save(cap.capture());
        assertThat(cap.getValue().getNivelCiudad()).isEqualTo(NivelCiudad.INTERMEDIA);
        assertThat(cap.getValue().getValor()).isEqualByComparingTo("15000");
    }

    @Test
    void sinListaDeServiciosEsUnError() {
        assertThatThrownBy(() -> servicio.importar(json("{}"), false, "admin")).isInstanceOf(ApiException.class);
    }
}
