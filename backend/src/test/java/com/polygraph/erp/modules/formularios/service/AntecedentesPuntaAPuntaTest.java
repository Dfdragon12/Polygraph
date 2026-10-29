package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.ProcesoTipoProgreso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.service.CalificacionProcesoService;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Prompt 7 de docs/antecedentes — de punta a punta, con los servicios REALES encadenados sobre H2:
 * la base como la dejaron V202/V204, la plantilla nueva de V207 (campos de hallazgo), el cálculo
 * por hallazgos, el guardado del analista, el Word con
 * la plantilla real (office-stamper de producción) y el motor del estudio. Caso C2, estudio
 * Estándar, datos inventados.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({FormularioService.class, CandidatoInfoService.class, FormularioDocumentoService.class, FormularioPlantillaWordService.class,
        CalculoSubprocesoService.class, CalculoSubservicioService.class, com.polygraph.erp.modules.riesgo.service.CentralRiesgoService.class,
        CalificacionRiesgoService.class, CalificacionProcesoService.class})
@DisplayName("Antecedentes de punta a punta (caso C2)")
class AntecedentesPuntaAPuntaTest {

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
    private ServicioSubproceso antecedentes;

    @BeforeEach
    void prepararEstudio() throws Exception {
        // La base como estaba (formulario de V204, catálogo de V202) y luego V207, que borra esa
        // plantilla y la crea desde cero con campos de hallazgo — igual que en producción.
        TipoProgreso tAnt = PlantillaAntecedentesPrueba.subservicio(em);
        jdbc.execute(PlantillaAntecedentesPrueba.insertDeV202());
        PlantillaAntecedentesPrueba.aplicarV204(jdbc);
        PlantillaAntecedentesPrueba.aplicarSoloV207(jdbc);
        em.clear();
        persistir(UmbralRiesgo.builder().criticidad(CriticidadCargo.ESTANDAR).limiteBajo(bd("30")).limiteMedio(bd("60")).minimoMedio(bd("31")).build());
        persistir(ParametrosRiesgo.builder().id(1).umbralServicioAlto(bd("70")).umbralServicioModerado(bd("50"))
                .cantidadServiciosModerados(2).version(1).fechaActualizacion(LocalDateTime.now()).build());
        tAnt = em.find(TipoProgreso.class, tAnt.getIdTipoProgreso());
        persistir(FormularioPlantillaConfig.builder().tipoProgreso(tAnt).plantillaWordRuta("antecedentes.docx").fechaCarga(LocalDateTime.now()).build());
        when(almacenamiento.cargarComoResource(anyString())).thenReturn(new FileSystemResource(PlantillaAntecedentesPrueba.WORD));

        // Estudio Avanzado (v1.8): Antecedentes 25, el resto aún sin cómo calificar (opción b).
        gestor = persistir(Usuario.builder().nombre("Gestora").apellido("Prueba").email("gestora.e2e@test.com").password("x")
                .rol(Rol.GESTOR).activo(true).emailVerificado(true).build());
        analista = persistir(Usuario.builder().nombre("Analista").apellido("Prueba").email("analista.e2e@test.com").password("x")
                .rol(Rol.ANALISTA_INTERNO).activo(true).emailVerificado(true).build());
        Cliente cliente = persistir(Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA)
                .razonSocial("Empresa Ficticia S.A.S.").emailPrincipal("ficticia.e2e@test.com").idGestor(gestor.getIdUsuario()).build());
        Candidato candidato = persistir(Candidato.builder().cedula("1000000002").tipoDocumento("CC").nombres("Persona").apellidos("Inventada").build());
        ClasificacionProceso clasificacion = persistir(ClasificacionProceso.builder().codigo("EST").nombre("Estudios").activo(true).build());
        Proceso avanzado = persistir(Proceso.builder().nombreProceso("AVANZADO").clasificacion(clasificacion).aplicaPrecioCiudad(false)
                .coberturaMinima(bd("60")).criticidadDefecto(CriticidadCargo.ESTANDAR).build());
        persistir(ProcesoTipoProgreso.builder().proceso(avanzado).tipoProgreso(tAnt).habilitado(true).obligatorio(true).peso(bd("25")).build());
        String[][] otros = {{"Poligrafía", "25"}, {"Validaciones", "20"}, {"Financiero", "15"}, {"Visita", "15"}};
        for (String[] o : otros) {
            TipoProgreso t = persistir(TipoProgreso.builder().nombreProgreso(o[0]).activo(true).rolResponsable(Rol.ANALISTA_INTERNO)
                    .requiereDocumento(false).versionPlantilla(1).build());
            persistir(ProcesoTipoProgreso.builder().proceso(avanzado).tipoProgreso(t).habilitado(true).obligatorio(false).peso(bd(o[1])).build());
        }
        servicio = persistir(Servicio.builder().cliente(cliente).candidato(candidato).proceso(avanzado).usuarioSolicita(gestor)
                .cargo("Auxiliar administrativo").fechaSolicitud(LocalDate.now()).estado(EstadoServicio.EN_EJECUCION).build());
        antecedentes = persistir(ServicioSubproceso.builder().servicio(servicio).tipoProgreso(tAnt).usuarioAsignado(analista)
                .estado(EstadoSubproceso.EN_EJECUCION).fechaCreacion(LocalDateTime.now()).versionPlantilla(tAnt.getVersionPlantilla()).build());
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Guardar → Word (sección 3, fórmula, semáforo, tránsito, procesos) → motor del estudio")
    void puntaAPunta() throws Exception {
        // 1-2. El analista guarda el formulario con los datos del caso C2.
        FormularioResponse formulario = formularioService.obtener(antecedentes.getId(), analista);
        FormularioResponse guardado = formularioService.guardar(antecedentes.getId(), casoC2(formulario), analista);

        assertThat(guardado.avisoCalculo()).isNull();
        assertThat(valorCampo(guardado, "srv_puntaje")).isEqualTo("60,0");
        assertThat(valorCampo(guardado, "srv_formula")).isEqualTo("30 + 15 + 15 = 60 de 100 posibles");
        assertThat(valorCampo(guardado, "sem_medio")).isEqualTo("▲ 60,0 puntos");
        assertThat(valorCampo(guardado, "sem_bajo")).isEmpty();
        assertThat(valorCampo(guardado, "sem_alto")).isEmpty();
        assertThat(filasTabla(guardado, "hallazgos")).extracting(f -> f.get("h_ref") + " " + f.get("h_puntos"))
                .containsExactly("LEG-18 30", "LEG-19 15", "LEG-19 15");
        assertThat(filasTabla(guardado, "procesos_privados")).extracting(f -> f.get("pp_numero")).containsExactly("1", "2");

        // Se completa (lo que hace "Marcar como completado") y se genera el Word.
        em.flush();
        em.clear();
        ServicioSubproceso sp = em.find(ServicioSubproceso.class, antecedentes.getId());
        sp.setEstado(EstadoSubproceso.FINALIZADO);
        sp.setFechaCompletado(LocalDateTime.now());
        em.flush();
        em.clear();

        byte[] word = documentoService.generar(antecedentes.getId(), gestor);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(word));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String texto = extractor.getText();
            assertThat(texto).doesNotContain("${");

            XWPFTable hallazgos = tabla(doc, "Ref.", "Hallazgo", "Fuente", "Detalle", "Puntos");
            assertThat(hallazgos.getRows()).hasSize(1 + 3 + 1); // encabezado, 3 hallazgos, fila de puntaje
            assertThat(List.of(celdas(hallazgos.getRow(1)).get(0), celdas(hallazgos.getRow(2)).get(0), celdas(hallazgos.getRow(3)).get(0)))
                    .containsExactly("LEG-18", "LEG-19", "LEG-19");
            assertThat(List.of(celdas(hallazgos.getRow(1)).get(4), celdas(hallazgos.getRow(2)).get(4), celdas(hallazgos.getRow(3)).get(4)))
                    .containsExactly("30", "15", "15");
            assertThat(celdas(hallazgos.getRow(4))).containsExactly("Puntaje del servicio: 30 + 15 + 15 = 60 de 100 posibles", "60,0");

            XWPFTable semaforo = tabla(doc, "SEMÁFORO DE RIESGO DEL SERVICIO");
            assertThat(celdas(semaforo.getRow(2))).containsExactly("", "▲ 60,0 puntos", "");

            assertThat(texto).contains("9. VALIDACIÓN DE INFORMACIÓN DE TRÁNSITO")
                    .contains("Informativo: el cargo no implica conducción; los resultados de tránsito no suman puntos.");
            // Sección 11 sin procesos (nota), sección 12 con un bloque por proceso, numerados.
            assertThat(texto).contains("No se encontraron procesos judiciales en Rama Judicial.")
                    .contains("PROCESO N° 1").contains("PROCESO N° 2").doesNotContain("PROCESO N° 3");
            // Datos del estudio que salen de la plataforma.
            assertThat(texto).contains("Empresa Ficticia S.A.S.").contains("Auxiliar administrativo");
        }

        // 3. El resultado llega al motor del estudio.
        CalificacionRiesgo estudio = riesgoService.recalcular(servicio.getIdServicio(), gestor.getIdUsuario());
        assertThat(estudio.getEstado()).isEqualTo(EstadoResultado.CALCULADO);
        assertThat(estudio.getCriticidad()).isEqualTo(CriticidadCargo.ESTANDAR); // la del formulario
        // Solo Antecedentes tiene cómo calificar: su peso es todo el estudio (opción b) → 60 → MEDIO.
        assertThat(estudio.getPuntajePonderado()).isEqualByComparingTo("60.0");
        assertThat(estudio.getNivelFinal()).isEqualTo(NivelRiesgo.MEDIO);
        // En el registro del cálculo, Antecedentes entra realizado con 60 y todo el peso efectivo; los
        // demás quedan SIN_CALIFICACION (no tienen todavía cómo calificar).
        assertThat(estudio.getDetalleJson())
                .contains("\"nombre\":\"Antecedentes\",\"estado\":\"REALIZADO\",\"peso\":25.00,\"pesoEfectivo\":100.0000,\"puntajeBruto\":60.0000")
                .contains("\"nombre\":\"Poligrafía (no incluido en el caso)\",\"estado\":\"SIN_CALIFICACION\"");
        assertThat(riesgoService.marcadoresWord(servicio.getIdServicio(), antecedentes.getId()).calificacion())
                .containsEntry("nivel", "MEDIO").containsEntry("puntaje", "60,0");
    }

    // ───────────────────────────── caso C2 ─────────────────────────────

    /** Entradas de C2 en casos_prueba.py, armadas sobre los ids reales del formulario. */
    private static GuardarFormularioRequest casoC2(FormularioResponse f) {
        Map<String, String> campos = Map.of("ev_criticidad", "Estándar", "ev_conduccion", "No", "ev_estado_doc", "Vigente",
                "dec1_coincide", "Sí", "dec2_coincide", "Sí", "dec3_coincide", "Sí", "simit_ref", "LEG-17", "simit_multas", "2 pendientes");
        Map<String, List<Map<String, String>>> tablas = Map.of(
                "listas_nacionales", List.of(
                        Map.of("ln_fuente", "Medidas correctivas (RNMC)", "ln_ref", "LEG-18", "ln_observacion", "Multa tipo 2 (2021)"),
                        Map.of("ln_fuente", "Medidas correctivas (RNMC)", "ln_ref", "LEG-18", "ln_observacion", "Multa tipo 1 (2019), en proceso")),
                "procesos_privados", List.of(
                        Map.of("pp_delito", "Hurto", "pp_estado", "Inactivo", "pp_calificacion", "LEG-19"),
                        Map.of("pp_delito", "Violencia intrafamiliar", "pp_estado", "Inactivo", "pp_calificacion", "LEG-19")));
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
                String clave = s.columnas().isEmpty() ? null : claveTabla(s);
                for (Map<String, String> fila : tablas.getOrDefault(clave, List.of())) {
                    Map<Integer, String> valores = new HashMap<>();
                    fila.forEach((k, v) -> valores.put(idPorClave.get(k), v));
                    filas.add(new FilaDatosRequest("", valores, null));
                }
                secciones.add(new SeccionDatosRequest(s.idSeccion(), null, filas));
            }
        }
        return new GuardarFormularioRequest(secciones);
    }

    /** La tabla se reconoce por el prefijo de sus columnas (ln_, pp_…), igual que en el Word. */
    private static String claveTabla(FormularioResponse.SeccionDatosResponse s) {
        String primera = s.columnas().get(0).clave();
        if (primera.startsWith("ln_")) return "listas_nacionales";
        if (primera.startsWith("pp_")) return "procesos_privados";
        return primera;
    }

    private static String valorCampo(FormularioResponse f, String clave) {
        for (var s : f.secciones()) {
            for (ColumnaResponse c : s.columnas()) {
                if (clave.equals(c.clave()) && "CAMPOS".equals(s.tipo())) return s.valores().get(c.idColumna());
            }
        }
        throw new AssertionError("No existe el campo " + clave);
    }

    private static List<Map<String, String>> filasTabla(FormularioResponse f, String prefijoOClave) {
        for (var s : f.secciones()) {
            if (!"TABLA".equals(s.tipo()) || s.columnas().isEmpty()) continue;
            String primera = s.columnas().get(0).clave();
            boolean es = "hallazgos".equals(prefijoOClave) ? primera.startsWith("h_") : primera.startsWith("pp_") && "procesos_privados".equals(prefijoOClave);
            if (!es) continue;
            List<Map<String, String>> filas = new ArrayList<>();
            for (var fila : s.filas()) {
                Map<String, String> porClave = new HashMap<>();
                for (ColumnaResponse c : s.columnas()) porClave.put(c.clave(), fila.valores().get(c.idColumna()));
                filas.add(porClave);
            }
            return filas;
        }
        throw new AssertionError("No existe la tabla " + prefijoOClave);
    }

    // ───────────────────────────── utilidades ─────────────────────────────

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
