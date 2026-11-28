package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.ProcesoTipoProgreso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.service.CalificacionProcesoService;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.entity.ExperienciaLaboralEvaluado;
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
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo;
import com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.*;
import jakarta.persistence.EntityManager;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
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
 * Validación laboral de punta a punta (V218), con los servicios REALES encadenados sobre H2: el analista guarda dos
 * vinculaciones (LAB-07 y LAB-04, una validada por sábana pensional), un periodo de inactividad sin corroborar y una
 * gestión; se genera el Word con la plantilla real y el resultado llega al motor del estudio. Datos inventados.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({FormularioService.class, CandidatoInfoService.class, FormularioDocumentoService.class, FormularioPlantillaWordService.class,
        CalculoSubprocesoService.class, CalculoSubservicioService.class, com.polygraph.erp.modules.riesgo.service.CentralRiesgoService.class,
        CalificacionRiesgoService.class, CalificacionProcesoService.class})
@DisplayName("Validación laboral de punta a punta")
class LaboralPuntaAPuntaTest {

    private static final Path WORD = Path.of("../docs/Validacion Laboral/VERIFICACION_LABORAL_PLATAFORMA_FINAL.docx");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private FormularioService formularioService;
    @Autowired private FormularioDocumentoService documentoService;
    @Autowired private CalificacionRiesgoService riesgoService;
    @MockBean private NotificacionService notificacionService;
    @MockBean private AlmacenamientoDocumentosService almacenamiento;

    private Usuario gestor;
    private Usuario analista;
    private Servicio servicio;
    private ServicioSubproceso laboral;

    @BeforeEach
    void prepararEstudio() throws Exception {
        TipoProgreso tLab = persistir(TipoProgreso.builder().nombreProgreso("Validacion Laboral").activo(true)
                .rolResponsable(Rol.ANALISTA_INTERNO).requiereDocumento(true).versionPlantilla(7).build());
        em.flush();
        jdbc.execute(PlantillaAntecedentesPrueba.insertDeV202());
        LaboralHallazgosTest.ejecutar(jdbc);
        em.clear();
        persistir(UmbralRiesgo.builder().criticidad(CriticidadCargo.ESTANDAR).limiteBajo(bd("30")).limiteMedio(bd("60")).minimoMedio(bd("31")).build());
        persistir(ParametrosRiesgo.builder().id(1).umbralServicioAlto(bd("70")).umbralServicioModerado(bd("50"))
                .cantidadServiciosModerados(2).version(1).fechaActualizacion(LocalDateTime.now()).build());
        tLab = em.find(TipoProgreso.class, tLab.getIdTipoProgreso());
        persistir(FormularioPlantillaConfig.builder().tipoProgreso(tLab).plantillaWordRuta("laboral.docx").fechaCarga(LocalDateTime.now()).build());
        when(almacenamiento.cargarComoResource(anyString())).thenReturn(new FileSystemResource(WORD));

        gestor = persistir(Usuario.builder().nombre("Gestora").apellido("Prueba").email("gestora.lab@test.com").password("x")
                .rol(Rol.GESTOR).activo(true).emailVerificado(true).build());
        analista = persistir(Usuario.builder().nombre("Analista").apellido("Prueba").email("analista.lab@test.com").password("x")
                .rol(Rol.ANALISTA_INTERNO).activo(true).emailVerificado(true).build());
        Cliente cliente = persistir(Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA)
                .razonSocial("Empresa Ficticia S.A.S.").emailPrincipal("ficticia.lab@test.com").idGestor(gestor.getIdUsuario()).build());
        Candidato candidato = persistir(Candidato.builder().cedula("1000000004").tipoDocumento("CC").nombres("Persona").apellidos("Inventada").build());
        HojaVidaEvaluado hoja = persistir(HojaVidaEvaluado.builder().candidato(candidato).autorizacionDatos(true).completado(true).build());
        hoja.getExperienciaLaboral().add(ExperienciaLaboralEvaluado.builder().hojaVidaEvaluado(hoja).empresa("Comercial Declarada Ltda")
                .cargo("Vendedor").fechaInicio(LocalDate.of(2022, 3, 1)).fechaFin(LocalDate.of(2024, 5, 31)).build());
        ClasificacionProceso clasificacion = persistir(ClasificacionProceso.builder().codigo("EST").nombre("Estudios").activo(true).build());
        Proceso avanzado = persistir(Proceso.builder().nombreProceso("AVANZADO").clasificacion(clasificacion).aplicaPrecioCiudad(false)
                .coberturaMinima(bd("60")).criticidadDefecto(CriticidadCargo.ESTANDAR).build());
        persistir(ProcesoTipoProgreso.builder().proceso(avanzado).tipoProgreso(tLab).habilitado(true).obligatorio(true).peso(bd("10")).build());
        TipoProgreso resto = persistir(TipoProgreso.builder().nombreProgreso("Resto del estudio").activo(true).rolResponsable(Rol.ANALISTA_INTERNO)
                .requiereDocumento(false).versionPlantilla(1).build());
        persistir(ProcesoTipoProgreso.builder().proceso(avanzado).tipoProgreso(resto).habilitado(true).obligatorio(false).peso(bd("90")).build());
        servicio = persistir(Servicio.builder().cliente(cliente).candidato(candidato).proceso(avanzado).usuarioSolicita(gestor)
                .cargo("Auxiliar administrativo").fechaSolicitud(LocalDate.now()).estado(EstadoServicio.EN_EJECUCION).build());
        laboral = persistir(ServicioSubproceso.builder().servicio(servicio).tipoProgreso(tLab).usuarioAsignado(analista)
                .estado(EstadoSubproceso.EN_EJECUCION).fechaCreacion(LocalDateTime.now()).versionPlantilla(tLab.getVersionPlantilla()).build());
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Guardar (LAB-07 + LAB-04, sábana, inactividad sin corroborar) → Word con un bloque por vinculación → motor del estudio")
    void puntaAPunta() throws Exception {
        FormularioResponse formulario = formularioService.obtener(laboral.getId(), analista);
        FormularioResponse guardado = formularioService.guardar(laboral.getId(), caso(formulario), analista);

        assertThat(guardado.avisoCalculo()).isNull();
        // 50 (LAB-07) + 45 (LAB-04) + 30 (LAB-13, una vez por estudio) = 125 → tope 100
        assertThat(valorCampo(guardado, "srv_formula")).isEqualTo("50 + 45 + 30 = 125 → 100 (tope máximo)");
        assertThat(valorCampo(guardado, "srv_puntaje")).isEqualTo("100,0");
        assertThat(valorCampo(guardado, "sem_alto")).isEqualTo("▲ 100,0 puntos");

        em.flush();
        em.clear();
        ServicioSubproceso sp = em.find(ServicioSubproceso.class, laboral.getId());
        sp.setEstado(EstadoSubproceso.FINALIZADO);
        sp.setFechaCompletado(LocalDateTime.now());
        em.flush();
        em.clear();

        byte[] word = documentoService.generar(laboral.getId(), gestor);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(word));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String texto = extractor.getText();
            assertThat(texto).doesNotContain("${");

            // Sección 1: la experiencia que declaró el candidato.
            assertThat(texto).contains("Comercial Declarada Ltda").contains("Vendedor");

            // Sección 3: tres hallazgos (dos vinculaciones y la inactividad, una sola vez).
            XWPFTable hallazgos = tabla(doc, "Ref.", "Hallazgo", "Fuente", "Detalle", "Puntos");
            assertThat(hallazgos.getRows()).hasSize(1 + 3 + 1);
            assertThat(List.of(celdas(hallazgos.getRow(1)).get(0), celdas(hallazgos.getRow(2)).get(0), celdas(hallazgos.getRow(3)).get(0)))
                    .containsExactly("LAB-07", "LAB-04", "LAB-13");

            // Sección 6: un bloque por vinculación, numerado, con la calificación y la nota de la sábana.
            assertThat(texto).contains("VINCULACIÓN LABORAL N° 1").contains("VINCULACIÓN LABORAL N° 2").doesNotContain("VINCULACIÓN LABORAL N° 3");
            assertThat(texto).contains("Se confirma la vinculación por medio de bases de datos públicas y privadas.");
            // Lo que no se imprime.
            assertThat(texto).doesNotContain("Llamó a la empresa").doesNotContain("Confirmado por la empresa");
            assertThat(texto).contains("Empresa Ficticia S.A.S.").contains("Auxiliar administrativo");
        }

        // El resultado llega al motor del estudio como servicio de peso 10.
        CalificacionRiesgo estudio = riesgoService.recalcular(servicio.getIdServicio(), gestor.getIdUsuario());
        assertThat(estudio.getEstado()).isEqualTo(EstadoResultado.CALCULADO);
        assertThat(estudio.getPuntajePonderado()).isEqualByComparingTo("100.0");
        assertThat(estudio.getNivelFinal()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(estudio.getDetalleJson()).contains("\"nombre\":\"Validacion Laboral\",\"estado\":\"REALIZADO\",\"peso\":10.00");
    }

    // ───────────────────────────── caso ─────────────────────────────

    private static GuardarFormularioRequest caso(FormularioResponse f) {
        Map<String, String> campos = new HashMap<>(Map.of("ev_criticidad", "Estándar", "lab_periodo_desde", "10/2022",
                "lab_periodo_hasta", "10/2026", "concepto", "Dos vinculaciones con novedad.", "analista_fecha", "15/10/2026"));
        Map<String, List<Map<String, String>>> tablas = Map.of(
                "vinculaciones", List.of(
                        Map.of("v_empresa", "Comercial Confirmada Ltda", "v_cargo", "Vendedor", "v_validado_por", "La empresa",
                                "v_ref1", "LAB-07", "v_lab01_base", "Confirmado por la empresa", "v_tipo", "Empresa"),
                        Map.of("v_empresa", "Servicios Inventados S.A.", "v_cargo", "Auxiliar", "v_validado_por", "Sábana pensional o bases de datos",
                                "v_ref1", "LAB-04", "v_tipo", "Empresa")),
                "inactividad", List.of(Map.of("in_desde", "01/06/2024", "in_hasta", "15/09/2024", "in_dias", "106",
                        "in_actividad", "Cuidado familiar", "in_corroboracion", "No se pudo corroborar")),
                "gestiones", List.of(Map.of("g_vinculacion", "1", "g_fecha", "10/10/2026", "g_medio", "Llamada", "g_destino", "Talento humano",
                        "g_resultado", "Llamó a la empresa")));
        List<SeccionDatosRequest> secciones = new ArrayList<>();
        for (FormularioResponse.SeccionDatosResponse s : f.secciones()) {
            Map<String, Integer> idPorClave = new HashMap<>();
            for (ColumnaResponse c : s.columnas()) idPorClave.put(c.clave(), c.idColumna());
            if ("CAMPOS".equals(s.tipo())) {
                Map<Integer, String> valores = new HashMap<>(s.valores());
                campos.forEach((clave, valor) -> { if (idPorClave.containsKey(clave)) valores.put(idPorClave.get(clave), valor); });
                secciones.add(new SeccionDatosRequest(s.idSeccion(), valores, null));
            } else {
                List<FilaDatosRequest> filas = new ArrayList<>();
                String clave = idPorClave.containsKey("v_empresa") ? "vinculaciones" : idPorClave.containsKey("in_desde") ? "inactividad"
                        : idPorClave.containsKey("g_fecha") ? "gestiones" : null;
                for (Map<String, String> fila : clave == null ? List.<Map<String, String>>of() : tablas.get(clave)) {
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

    private static XWPFTable tabla(XWPFDocument doc, String... encabezado) {
        return doc.getTables().stream().filter(t -> !t.getRows().isEmpty() && celdas(t.getRow(0)).equals(List.of(encabezado)))
                .findFirst().orElseThrow(() -> new AssertionError("No hay una tabla con encabezado " + List.of(encabezado)));
    }

    private static List<String> celdas(XWPFTableRow fila) {
        return fila.getTableCells().stream().map(c -> c.getText().trim()).toList();
    }

    private <T> T persistir(T entidad) {
        em.persist(entidad);
        return entidad;
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
