package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.ProcesoTipoProgreso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.service.CalificacionProcesoService;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService;
import com.polygraph.erp.modules.formularios.dto.ColumnaResponse;
import com.polygraph.erp.modules.formularios.dto.FormularioResponse;
import com.polygraph.erp.modules.formularios.dto.GuardarFormularioRequest;
import com.polygraph.erp.modules.formularios.dto.GuardarFormularioRequest.FilaDatosRequest;
import com.polygraph.erp.modules.formularios.dto.GuardarFormularioRequest.SeccionDatosRequest;
import com.polygraph.erp.modules.formularios.entity.FormularioPlantillaConfig;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService;
import com.polygraph.erp.modules.riesgo.entity.CalificacionRiesgo;
import com.polygraph.erp.modules.riesgo.entity.ParametrosRiesgo;
import com.polygraph.erp.modules.riesgo.entity.UmbralRiesgo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.EstadoResultado;
import com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.*;
import jakarta.persistence.EntityManager;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Análisis financiero de punta a punta (V221), con los servicios REALES encadenados sobre H2 y DOS estudios de prueba
 * (datos inventados): uno Avanzado que no es OEA (solo la consulta de síntesis) y uno OEA con estudio anterior. En cada
 * uno se guarda el formulario, se genera el Word con la plantilla real y el resultado llega al motor del estudio.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({FormularioService.class, CandidatoInfoService.class, FormularioDocumentoService.class, FormularioPlantillaWordService.class,
        CalculoSubprocesoService.class, CalculoSubservicioService.class, com.polygraph.erp.modules.riesgo.service.CentralRiesgoService.class,
        CalificacionRiesgoService.class, CalificacionProcesoService.class})
@DisplayName("Análisis financiero de punta a punta")
class FinancieroPuntaAPuntaTest {

    private static final Path WORD = FinancieroHallazgosTest.WORD;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private FormularioService formularioService;
    @Autowired private FormularioDocumentoService documentoService;
    @Autowired private CalificacionRiesgoService riesgoService;
    @MockBean private NotificacionService notificacionService;
    @MockBean private AlmacenamientoDocumentosService almacenamiento;

    private Usuario gestor;
    private Usuario analista;
    private TipoProgreso financiero;
    private int estudios;

    @BeforeEach
    void prepararPlantilla() throws Exception {
        financiero = persistir(TipoProgreso.builder().nombreProgreso("Analisis Financiero").activo(true)
                .rolResponsable(Rol.ANALISTA_INTERNO).requiereDocumento(true).versionPlantilla(7).build());
        em.flush();
        jdbc.execute(PlantillaAntecedentesPrueba.insertDeV202());
        FinancieroHallazgosTest.ejecutar(jdbc);
        em.clear();
        persistir(UmbralRiesgo.builder().criticidad(CriticidadCargo.ESTANDAR).limiteBajo(bd("30")).limiteMedio(bd("60")).minimoMedio(bd("31")).build());
        persistir(ParametrosRiesgo.builder().id(1).umbralServicioAlto(bd("70")).umbralServicioModerado(bd("50"))
                .cantidadServiciosModerados(2).version(1).fechaActualizacion(LocalDateTime.now()).build());
        financiero = em.find(TipoProgreso.class, financiero.getIdTipoProgreso());
        persistir(FormularioPlantillaConfig.builder().tipoProgreso(financiero).plantillaWordRuta("financiero.docx").fechaCarga(LocalDateTime.now()).build());
        when(almacenamiento.cargarComoResource(anyString())).thenReturn(new FileSystemResource(WORD));
        gestor = persistir(Usuario.builder().nombre("Gestora").apellido("Prueba").email("gestora.fin@test.com").password("x")
                .rol(Rol.GESTOR).activo(true).emailVerificado(true).build());
        analista = persistir(Usuario.builder().nombre("Analista").apellido("Prueba").email("analista.fin@test.com").password("x")
                .rol(Rol.ANALISTA_INTERNO).activo(true).emailVerificado(true).build());
    }

    /** Un estudio del proceso dado con el subproceso financiero asignado. */
    private ServicioSubproceso crearEstudio(String nombreProceso) {
        int n = ++estudios;
        Cliente cliente = persistir(Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA)
                .razonSocial("Empresa Ficticia S.A.S.").emailPrincipal("ficticia.fin" + n + "@test.com").idGestor(gestor.getIdUsuario()).build());
        Candidato candidato = persistir(Candidato.builder().cedula("100000000" + n).tipoDocumento("CC").nombres("Persona").apellidos("Inventada").build());
        persistir(HojaVidaEvaluado.builder().candidato(candidato).autorizacionDatos(true).completado(true).build());
        ClasificacionProceso clasificacion = persistir(ClasificacionProceso.builder().codigo("EST" + n).nombre("Estudios").activo(true).build());
        Proceso proceso = persistir(Proceso.builder().nombreProceso(nombreProceso).clasificacion(clasificacion).aplicaPrecioCiudad(false)
                .coberturaMinima(bd("60")).criticidadDefecto(CriticidadCargo.ESTANDAR).build());
        persistir(ProcesoTipoProgreso.builder().proceso(proceso).tipoProgreso(financiero).habilitado(true).obligatorio(true).peso(bd("15")).build());
        TipoProgreso resto = persistir(TipoProgreso.builder().nombreProgreso("Resto del estudio").activo(true).rolResponsable(Rol.ANALISTA_INTERNO)
                .requiereDocumento(false).versionPlantilla(1).build());
        persistir(ProcesoTipoProgreso.builder().proceso(proceso).tipoProgreso(resto).habilitado(true).obligatorio(false).peso(bd("85")).build());
        Servicio servicio = persistir(Servicio.builder().cliente(cliente).candidato(candidato).proceso(proceso).usuarioSolicita(gestor)
                .cargo("Auxiliar administrativo").fechaSolicitud(LocalDate.now()).estado(EstadoServicio.EN_EJECUCION).build());
        ServicioSubproceso sp = persistir(ServicioSubproceso.builder().servicio(servicio).tipoProgreso(financiero).usuarioAsignado(analista)
                .estado(EstadoSubproceso.EN_EJECUCION).fechaCreacion(LocalDateTime.now()).versionPlantilla(financiero.getVersionPlantilla()).build());
        em.flush();
        em.clear();
        return sp;
    }

    @Test
    @DisplayName("es_oea sale del nombre del proceso, no de lo que diga el analista")
    void esOea() {
        ServicioSubproceso normal = crearEstudio("AVANZADO");
        assertThat(valorCampo(formularioService.obtener(normal.getId(), analista), "es_oea")).isEqualTo("No");
        ServicioSubproceso oea = crearEstudio("OEA");
        assertThat(valorCampo(formularioService.obtener(oea.getId(), analista), "es_oea")).isEqualTo("Sí");
    }

    @Test
    @DisplayName("Avanzado NO OEA: FIN-02 (45), semáforo MEDIO, con salvedad, sin secciones 8 y 9 y concepto en la sección 8")
    void avanzadoNoOea() throws Exception {
        ServicioSubproceso sp = crearEstudio("AVANZADO");
        Map<String, String> campos = new HashMap<>(Map.of("ev_criticidad", "Estándar", "fin_meses_mora_90", "2", "fin_meses_mora_30_60", "2",
                "fin_ingreso", "2800", "fin_endeudamiento", "38", "cr_score", "512", "concepto", "Dos meses recientes con mora de 90 días o más.",
                "analista_fecha", "22/10/2026", "cr_fecha", "20/10/2026"));
        campos.put("cr_mayor_mora", "Mora 90 o más");
        campos.put("s_fin_vig", "1");
        campos.put("s_fin_cuota", "734");
        campos.put("s_tel_vig", "1");
        campos.put("s_tel_cuota", "386");
        campos.put("s_tel_mora", "386");
        FormularioResponse guardado = formularioService.guardar(sp.getId(), caso(formularioService.obtener(sp.getId(), analista), campos, Map.of()), analista);

        assertThat(guardado.avisoCalculo()).isNull();
        assertThat(valorCampo(guardado, "srv_formula")).isEqualTo("FIN-02: 45 de 100 posibles");
        assertThat(valorCampo(guardado, "srv_puntaje")).isEqualTo("45,0");
        assertThat(valorCampo(guardado, "sem_medio")).isEqualTo("▲ 45,0 puntos");
        assertThat(valorCampo(guardado, "s_tot_cuota")).isEqualTo("1.120");
        assertThat(valorCampo(guardado, "cr_cuotas")).isEqualTo("$1.120 mil");
        assertThat(valorCampo(guardado, "sec_concepto")).isEqualTo("8");

        String texto = generarWord(sp);
        assertThat(texto).doesNotContain("${");
        assertThat(texto).contains("FIN-02").contains("Mora 90 o más en los 2 meses más recientes; saldo en mora $386 mil").contains("▲ 45,0 puntos");
        assertThat(texto).contains("$1.120 mil").contains("$2.800 mil").contains("38 %").contains("1.120").contains("734");
        assertThat(texto).contains("Alcance: la consulta de síntesis muestra los totales por sector");
        assertThat(texto).doesNotContain("OBLIGACIONES CON REPORTE NEGATIVO").doesNotContain("DECLARACIÓN DE RENTA Y PATRIMONIO");
        assertThat(texto).contains("8. CONCEPTO FINAL DEL ESTUDIO FINANCIERO").doesNotContain("10. CONCEPTO FINAL");
        assertThat(texto).contains("Indicador de pago (score)").contains("512 – banda Medio").contains("501 a 600 Medio");
        assertThat(texto).doesNotContain("Primer estudio");

        CalificacionRiesgo estudio = riesgoService.recalcular(sp.getServicio().getIdServicio(), gestor.getIdUsuario());
        assertThat(estudio.getEstado()).isEqualTo(EstadoResultado.CALCULADO);
        assertThat(estudio.getDetalleJson()).contains("\"nombre\":\"Analisis Financiero\",\"estado\":\"REALIZADO\",\"peso\":15.00");
    }

    @Test
    @DisplayName("OEA con estudio anterior: castigada y dos bienes (Sin cambio y Nuevo con explicación); secciones 8 y 9, concepto en la 10, sin salvedad")
    void oeaConEstudioAnterior() throws Exception {
        ServicioSubproceso sp = crearEstudio("OEA");
        Map<String, String> campos = new HashMap<>(Map.of("ev_criticidad", "Estándar", "fin_meses_mora_90", "6", "fin_meses_mora_30_60", "0",
                "fin_ingreso", "3500", "fin_endeudamiento", "40", "cr_score", "430", "concepto", "Una obligación castigada y bienes con cambios.",
                "analista_fecha", "22/10/2026", "cr_fecha", "20/10/2026"));
        campos.put("s_real_vig", "2");
        campos.put("s_real_mora", "1900");
        campos.put("s_real_cuota", "900");
        campos.put("oea_anterior", "Estudio N° 1234 del 03/03/2026");
        campos.put("oea_obs", "Consulta de propietarios corroborada en la visita.");
        Map<String, List<Map<String, String>>> tablas = Map.of(
                "obligaciones_negativas", List.of(FinancieroHallazgosTest.ob("Almacén de ejemplo", "Castigada", 400, 1900, "Principal", "CAU")),
                "patrimonios", List.of(FinancieroHallazgosTest.pt("Apartamento", "Sin cambio", ""),
                        FinancieroHallazgosTest.pt("Vehículo", "Nuevo", "Compra con crédito aprobado")));
        FormularioResponse guardado = formularioService.guardar(sp.getId(), caso(formularioService.obtener(sp.getId(), analista), campos, tablas), analista);

        assertThat(guardado.avisoCalculo()).isNull();
        assertThat(valorCampo(guardado, "srv_formula")).isEqualTo("FIN-01: 60 de 100 posibles");
        assertThat(valorCampo(guardado, "srv_puntaje")).isEqualTo("60,0");
        assertThat(valorCampo(guardado, "sec_concepto")).isEqualTo("10");

        String texto = generarWord(sp);
        assertThat(texto).doesNotContain("${");
        assertThat(texto).contains("8. OBLIGACIONES CON REPORTE NEGATIVO").contains("9. DECLARACIÓN DE RENTA Y PATRIMONIO");
        assertThat(texto).contains("Almacén de ejemplo").contains("CAU").contains("Castigada").contains("400 días").contains("1.900");
        assertThat(texto).contains("Estudio anterior:").contains("Estudio N° 1234 del 03/03/2026");
        assertThat(texto).contains("Apartamento").contains("Sin cambio").contains("Vehículo").contains("Nuevo: Compra con crédito aprobado");
        assertThat(texto).contains("Consulta de propietarios corroborada en la visita.");
        assertThat(texto).contains("10. CONCEPTO FINAL DEL ESTUDIO FINANCIERO").doesNotContain("8. CONCEPTO FINAL");
        assertThat(texto).doesNotContain("Alcance: la consulta de síntesis muestra los totales por sector");
        assertThat(texto).doesNotContain("No registra obligaciones con reporte negativo");
        assertThat(texto).contains("430 – banda Bajo");
        // Lo que no se imprime.
        assertThat(texto).doesNotContain("Compra con crédito aprobado.").doesNotContain("Mora 90 o más en los");

        CalificacionRiesgo estudio = riesgoService.recalcular(sp.getServicio().getIdServicio(), gestor.getIdUsuario());
        assertThat(estudio.getEstado()).isEqualTo(EstadoResultado.CALCULADO);
        assertThat(estudio.getDetalleJson()).contains("\"nombre\":\"Analisis Financiero\",\"estado\":\"REALIZADO\",\"peso\":15.00");
    }

    // ───────────────────────────── apoyo ─────────────────────────────

    private String generarWord(ServicioSubproceso sp) throws Exception {
        em.flush();
        em.clear();
        ServicioSubproceso actual = em.find(ServicioSubproceso.class, sp.getId());
        actual.setEstado(EstadoSubproceso.FINALIZADO);
        actual.setFechaCompletado(LocalDateTime.now());
        em.flush();
        em.clear();
        byte[] word = documentoService.generar(sp.getId(), gestor);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(word)); XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private GuardarFormularioRequest caso(FormularioResponse f, Map<String, String> campos, Map<String, List<Map<String, String>>> tablas) {
        List<SeccionDatosRequest> secciones = new ArrayList<>();
        for (FormularioResponse.SeccionDatosResponse s : f.secciones()) {
            Map<String, Integer> idPorClave = new HashMap<>();
            for (ColumnaResponse c : s.columnas()) idPorClave.put(c.clave(), c.idColumna());
            if ("CAMPOS".equals(s.tipo())) {
                Map<Integer, String> valores = new HashMap<>(s.valores());
                campos.forEach((clave, valor) -> { if (idPorClave.containsKey(clave)) valores.put(idPorClave.get(clave), valor); });
                secciones.add(new SeccionDatosRequest(s.idSeccion(), valores, null));
            } else {
                String clave = idPorClave.containsKey("o_entidad") ? "obligaciones_negativas" : idPorClave.containsKey("pt_tipo") ? "patrimonios" : null;
                List<FilaDatosRequest> filas = new ArrayList<>();
                for (Map<String, String> fila : clave == null ? List.<Map<String, String>>of() : tablas.getOrDefault(clave, List.of())) {
                    Map<Integer, String> valores = new HashMap<>();
                    fila.forEach((k, v) -> valores.put(idPorClave.get(k), v));
                    filas.add(new FilaDatosRequest("", valores, null));
                }
                secciones.add(new SeccionDatosRequest(s.idSeccion(), null, filas));
            }
        }
        return new GuardarFormularioRequest(secciones);
    }

    private static String valorCampo(FormularioResponse f, String clave) {
        for (var s : f.secciones()) {
            for (ColumnaResponse c : s.columnas()) {
                if (clave.equals(c.clave()) && "CAMPOS".equals(s.tipo())) return s.valores().get(c.idColumna());
            }
        }
        throw new AssertionError("No existe el campo " + clave);
    }

    private <T> T persistir(T entidad) {
        em.persist(entidad);
        return entidad;
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
