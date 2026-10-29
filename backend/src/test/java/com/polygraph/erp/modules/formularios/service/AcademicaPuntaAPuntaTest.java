package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.ProcesoTipoProgreso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.service.CalificacionProcesoService;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.entity.EducacionEvaluado;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Prompt 7 de docs/Validacion Academica — de punta a punta, con los servicios REALES encadenados sobre H2:
 * la plantilla de V216, el guardado del analista (caso A4, ACA-03, con dos gestiones), el Word con la
 * plantilla real y el motor del estudio. Datos inventados.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({FormularioService.class, CandidatoInfoService.class, FormularioDocumentoService.class, FormularioPlantillaWordService.class,
        CalculoSubprocesoService.class, CalculoSubservicioService.class, com.polygraph.erp.modules.riesgo.service.CentralRiesgoService.class,
        CalificacionRiesgoService.class, CalificacionProcesoService.class})
@DisplayName("Validación académica de punta a punta (caso A4)")
class AcademicaPuntaAPuntaTest {

    private static final Path WORD = Path.of("../docs/Validacion Academica/VERIFICACION_ACADEMICA_PLATAFORMA_FINAL.docx");
    private static final Path V216 = Path.of("src/main/resources/db/migration/V216__plantilla_validacion_academica.sql");

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
    private ServicioSubproceso academica;

    @BeforeEach
    void prepararEstudio() throws Exception {
        TipoProgreso tAca = persistir(TipoProgreso.builder().nombreProgreso("Validacion Academica").activo(true)
                .rolResponsable(Rol.ANALISTA_INTERNO).requiereDocumento(true).versionPlantilla(23).build());
        em.flush();
        jdbc.execute(PlantillaAntecedentesPrueba.insertDeV202());
        String sql = Files.readAllLines(V216, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.trim().startsWith("--")).collect(Collectors.joining("\n"));
        for (String sentencia : sql.split(";\\s*\\n")) {
            if (!sentencia.isBlank()) jdbc.execute(sentencia.replaceAll(";\\s*$", ""));
        }
        em.clear();
        persistir(UmbralRiesgo.builder().criticidad(CriticidadCargo.ESTANDAR).limiteBajo(bd("30")).limiteMedio(bd("60")).minimoMedio(bd("31")).build());
        persistir(ParametrosRiesgo.builder().id(1).umbralServicioAlto(bd("70")).umbralServicioModerado(bd("50"))
                .cantidadServiciosModerados(2).version(1).fechaActualizacion(LocalDateTime.now()).build());
        tAca = em.find(TipoProgreso.class, tAca.getIdTipoProgreso());
        persistir(FormularioPlantillaConfig.builder().tipoProgreso(tAca).plantillaWordRuta("academica.docx").fechaCarga(LocalDateTime.now()).build());
        when(almacenamiento.cargarComoResource(anyString())).thenReturn(new FileSystemResource(WORD));

        gestor = persistir(Usuario.builder().nombre("Gestora").apellido("Prueba").email("gestora.aca@test.com").password("x")
                .rol(Rol.GESTOR).activo(true).emailVerificado(true).build());
        analista = persistir(Usuario.builder().nombre("Analista").apellido("Prueba").email("analista.aca@test.com").password("x")
                .rol(Rol.ANALISTA_INTERNO).activo(true).emailVerificado(true).build());
        Cliente cliente = persistir(Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA)
                .razonSocial("Empresa Ficticia S.A.S.").emailPrincipal("ficticia.aca@test.com").idGestor(gestor.getIdUsuario()).build());
        Candidato candidato = persistir(Candidato.builder().cedula("1000000003").tipoDocumento("CC").nombres("Persona").apellidos("Inventada").build());
        HojaVidaEvaluado hoja = persistir(HojaVidaEvaluado.builder().candidato(candidato).autorizacionDatos(true).completado(true).build());
        hoja.getEducacion().add(EducacionEvaluado.builder().hojaVidaEvaluado(hoja).nivel("Profesional").institucion("Universidad Ficticia")
                .titulo("Administrador de empresas").fechaFin(LocalDate.of(2018, 6, 15)).build());
        hoja.getEducacion().add(EducacionEvaluado.builder().hojaVidaEvaluado(hoja).nivel("Bachiller").institucion("Colegio Inventado")
                .titulo("Bachiller académico").fechaFin(LocalDate.of(2012, 11, 30)).build());
        ClasificacionProceso clasificacion = persistir(ClasificacionProceso.builder().codigo("EST").nombre("Estudios").activo(true).build());
        Proceso avanzado = persistir(Proceso.builder().nombreProceso("AVANZADO").clasificacion(clasificacion).aplicaPrecioCiudad(false)
                .coberturaMinima(bd("60")).criticidadDefecto(CriticidadCargo.ESTANDAR).build());
        persistir(ProcesoTipoProgreso.builder().proceso(avanzado).tipoProgreso(tAca).habilitado(true).obligatorio(true).peso(bd("6")).build());
        // El resto del Avanzado (94 %) aún sin cómo calificar: el proceso suma 100 y la académica entra con su peso de 6.
        TipoProgreso resto = persistir(TipoProgreso.builder().nombreProgreso("Resto del estudio").activo(true).rolResponsable(Rol.ANALISTA_INTERNO)
                .requiereDocumento(false).versionPlantilla(1).build());
        persistir(ProcesoTipoProgreso.builder().proceso(avanzado).tipoProgreso(resto).habilitado(true).obligatorio(false).peso(bd("94")).build());
        servicio = persistir(Servicio.builder().cliente(cliente).candidato(candidato).proceso(avanzado).usuarioSolicita(gestor)
                .cargo("Auxiliar administrativo").fechaSolicitud(LocalDate.now()).estado(EstadoServicio.EN_EJECUCION).build());
        academica = persistir(ServicioSubproceso.builder().servicio(servicio).tipoProgreso(tAca).usuarioAsignado(analista)
                .estado(EstadoSubproceso.EN_EJECUCION).fechaCreacion(LocalDateTime.now()).versionPlantilla(tAca.getVersionPlantilla()).build());
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Guardar (ACA-03 + 2 gestiones) → Word (secciones 1, 3 y 5; sin gestiones) → motor del estudio")
    void puntaAPunta() throws Exception {
        FormularioResponse formulario = formularioService.obtener(academica.getId(), analista);
        FormularioResponse guardado = formularioService.guardar(academica.getId(), casoA4(formulario), analista);

        assertThat(guardado.avisoCalculo()).isNull();
        assertThat(valorCampo(guardado, "srv_puntaje")).isEqualTo("45,0");
        assertThat(valorCampo(guardado, "srv_formula")).isEqualTo("ACA-03: 45 de 100 posibles");
        assertThat(valorCampo(guardado, "sem_medio")).isEqualTo("▲ 45,0 puntos");
        assertThat(valorCampo(guardado, "sem_bajo")).isEmpty();
        assertThat(valorCampo(guardado, "sem_alto")).isEmpty();

        em.flush();
        em.clear();
        ServicioSubproceso sp = em.find(ServicioSubproceso.class, academica.getId());
        sp.setEstado(EstadoSubproceso.FINALIZADO);
        sp.setFechaCompletado(LocalDateTime.now());
        em.flush();
        em.clear();

        byte[] word = documentoService.generar(academica.getId(), gestor);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(word));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String texto = extractor.getText();
            assertThat(texto).doesNotContain("${");

            // Sección 1: todos los estudios que declaró el candidato.
            XWPFTable educacion = tabla(doc, "INFORMACIÓN ACADÉMICA DECLARADA POR EL EVALUADO");
            List<List<String>> filasEducacion = educacion.getRows().stream().map(AcademicaPuntaAPuntaTest::celdas).toList();
            assertThat(filasEducacion).anySatisfy(f -> assertThat(f).containsExactly("Institución", "Nivel específico", "Título obtenido", "Fecha de finalización"));
            assertThat(filasEducacion).anySatisfy(f -> assertThat(f).containsExactly("Universidad Ficticia", "Profesional", "Administrador de empresas", "15/06/2018"));
            assertThat(filasEducacion).anySatisfy(f -> assertThat(f).containsExactly("Colegio Inventado", "Bachiller", "Bachiller académico", "30/11/2012"));
            assertThat(texto).contains("Universidad Ficticia").contains("Colegio Inventado");

            // Sección 3: una fila ACA-03 con 45 puntos y la fórmula.
            XWPFTable hallazgos = tabla(doc, "Ref.", "Hallazgo", "Fuente", "Detalle", "Puntos");
            assertThat(hallazgos.getRows()).hasSize(1 + 1 + 1);
            assertThat(celdas(hallazgos.getRow(1))).containsExactly("ACA-03",
                    "Datos que no coinciden con lo declarado (diferencia de fondo)", "Base de datos de la institución",
                    "Licenciatura en ejemplo – Universidad Ficticia", "45");
            assertThat(celdas(hallazgos.getRow(2))).containsExactly("Puntaje del servicio: ACA-03: 45 de 100 posibles", "45,0");

            XWPFTable semaforo = tabla(doc, "SEMÁFORO DE RIESGO DEL SERVICIO");
            assertThat(celdas(semaforo.getRow(2))).containsExactly("", "▲ 45,0 puntos", "");

            // Sección 5: criterio y calificación; las gestiones y lo interno NO salen.
            assertThat(texto).contains("Último estudio finalizado").contains("Calificación:");
            assertThat(texto).doesNotContain("Llamó a la secretaría").doesNotContain("Correo a registro académico")
                    .doesNotContain("Confirmado por la institución");
            assertThat(texto).contains("Empresa Ficticia S.A.S.").contains("Auxiliar administrativo");
        }

        // El resultado llega al motor del estudio como servicio de peso 6.
        CalificacionRiesgo estudio = riesgoService.recalcular(servicio.getIdServicio(), gestor.getIdUsuario());
        assertThat(estudio.getEstado()).isEqualTo(EstadoResultado.CALCULADO);
        assertThat(estudio.getCriticidad()).isEqualTo(CriticidadCargo.ESTANDAR);
        assertThat(estudio.getPuntajePonderado()).isEqualByComparingTo("45.0");
        assertThat(estudio.getNivelFinal()).isEqualTo(NivelRiesgo.MEDIO);
        assertThat(estudio.getDetalleJson()).contains("\"nombre\":\"Validacion Academica\",\"estado\":\"REALIZADO\",\"peso\":6.00");
    }

    // ───────────────────────────── caso A4 ─────────────────────────────

    private static GuardarFormularioRequest casoA4(FormularioResponse f) {
        Map<String, String> campos = new HashMap<>(Map.of("ev_criticidad", "Estándar", "aca_ref", "ACA-03",
                "aca_criterio", "Último estudio finalizado", "aca_institucion", "Universidad Ficticia", "aca_titulo", "Licenciatura en ejemplo",
                "aca_medio", "Base de datos de la institución", "aca_ac01_base", "Confirmado por la institución",
                "concepto", "El título no coincide con lo declarado.", "analista_fecha", "15/10/2026"));
        List<Map<String, String>> gestiones = List.of(
                Map.of("g_fecha", "10/10/2026", "g_medio", "Llamada", "g_destino", "Secretaría académica", "g_resultado", "Llamó a la secretaría"),
                Map.of("g_fecha", "11/10/2026", "g_medio", "Correo electrónico", "g_destino", "Registro académico", "g_resultado", "Correo a registro académico"));
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
                if (idPorClave.containsKey("g_fecha")) {
                    for (Map<String, String> fila : gestiones) {
                        Map<Integer, String> valores = new HashMap<>();
                        fila.forEach((k, v) -> valores.put(idPorClave.get(k), v));
                        filas.add(new FilaDatosRequest("", valores, null));
                    }
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
