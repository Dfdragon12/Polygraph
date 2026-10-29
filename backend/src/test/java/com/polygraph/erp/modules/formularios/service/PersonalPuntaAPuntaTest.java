package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.ProcesoTipoProgreso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.service.CalificacionProcesoService;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.evaluados.entity.ReferenciaPersonalEvaluado;
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
 * Validación personal de punta a punta (V219), con los servicios REALES encadenados sobre H2: el candidato registró 4
 * referencias; el analista ve esas 4 precargadas, marca la 2 "no contesta", valida la 1 y la 3 y la 3 contradice lo
 * verificado (PER-02); se genera el Word con la plantilla real y el resultado llega al motor del estudio. Datos inventados.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({FormularioService.class, CandidatoInfoService.class, FormularioDocumentoService.class, FormularioPlantillaWordService.class,
        CalculoSubprocesoService.class, CalculoSubservicioService.class, com.polygraph.erp.modules.riesgo.service.CentralRiesgoService.class,
        CalificacionRiesgoService.class, CalificacionProcesoService.class})
@DisplayName("Validación personal de punta a punta")
class PersonalPuntaAPuntaTest {

    private static final Path WORD = PersonalHallazgosTest.WORD;
    private static final String[] TELEFONOS = {"3001112233", "3002223344", "3003334455", "3004445566"};

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
    private ServicioSubproceso personal;

    @BeforeEach
    void prepararEstudio() throws Exception {
        TipoProgreso tPer = persistir(TipoProgreso.builder().nombreProgreso("Validacion Personal").activo(true)
                .rolResponsable(Rol.ANALISTA_INTERNO).requiereDocumento(true).versionPlantilla(7).build());
        em.flush();
        jdbc.execute(PlantillaAntecedentesPrueba.insertDeV202());
        PersonalHallazgosTest.ejecutar(jdbc);
        em.clear();
        persistir(UmbralRiesgo.builder().criticidad(CriticidadCargo.ESTANDAR).limiteBajo(bd("30")).limiteMedio(bd("60")).minimoMedio(bd("31")).build());
        persistir(ParametrosRiesgo.builder().id(1).umbralServicioAlto(bd("70")).umbralServicioModerado(bd("50"))
                .cantidadServiciosModerados(2).version(1).fechaActualizacion(LocalDateTime.now()).build());
        tPer = em.find(TipoProgreso.class, tPer.getIdTipoProgreso());
        persistir(FormularioPlantillaConfig.builder().tipoProgreso(tPer).plantillaWordRuta("personal.docx").fechaCarga(LocalDateTime.now()).build());
        when(almacenamiento.cargarComoResource(anyString())).thenReturn(new FileSystemResource(WORD));

        gestor = persistir(Usuario.builder().nombre("Gestora").apellido("Prueba").email("gestora.per@test.com").password("x")
                .rol(Rol.GESTOR).activo(true).emailVerificado(true).build());
        analista = persistir(Usuario.builder().nombre("Analista").apellido("Prueba").email("analista.per@test.com").password("x")
                .rol(Rol.ANALISTA_INTERNO).activo(true).emailVerificado(true).build());
        Cliente cliente = persistir(Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA)
                .razonSocial("Empresa Ficticia S.A.S.").emailPrincipal("ficticia.per@test.com").idGestor(gestor.getIdUsuario()).build());
        Candidato candidato = persistir(Candidato.builder().cedula("1000000005").tipoDocumento("CC").nombres("Persona").apellidos("Inventada")
                .celular("3100000000").build());
        HojaVidaEvaluado hoja = persistir(HojaVidaEvaluado.builder().candidato(candidato).autorizacionDatos(true).completado(true).build());
        String[] nombres = {"Ana Ejemplo", "Beto Ejemplo", "Carla Ejemplo", "Dario Ejemplo"};
        String[] parentescos = {"Amiga", "Vecino", "Colega", "Amigo"};
        for (int i = 0; i < 4; i++) {
            hoja.getReferencias().add(ReferenciaPersonalEvaluado.builder().hojaVidaEvaluado(hoja).nombre(nombres[i])
                    .parentesco(parentescos[i]).telefono(TELEFONOS[i]).tiempoConocimiento((i + 2) + " años").build());
        }
        ClasificacionProceso clasificacion = persistir(ClasificacionProceso.builder().codigo("EST").nombre("Estudios").activo(true).build());
        Proceso avanzado = persistir(Proceso.builder().nombreProceso("AVANZADO").clasificacion(clasificacion).aplicaPrecioCiudad(false)
                .coberturaMinima(bd("60")).criticidadDefecto(CriticidadCargo.ESTANDAR).build());
        persistir(ProcesoTipoProgreso.builder().proceso(avanzado).tipoProgreso(tPer).habilitado(true).obligatorio(true).peso(bd("4")).build());
        TipoProgreso resto = persistir(TipoProgreso.builder().nombreProgreso("Resto del estudio").activo(true).rolResponsable(Rol.ANALISTA_INTERNO)
                .requiereDocumento(false).versionPlantilla(1).build());
        persistir(ProcesoTipoProgreso.builder().proceso(avanzado).tipoProgreso(resto).habilitado(true).obligatorio(false).peso(bd("96")).build());
        servicio = persistir(Servicio.builder().cliente(cliente).candidato(candidato).proceso(avanzado).usuarioSolicita(gestor)
                .cargo("Auxiliar administrativo").fechaSolicitud(LocalDate.now()).estado(EstadoServicio.EN_EJECUCION).build());
        personal = persistir(ServicioSubproceso.builder().servicio(servicio).tipoProgreso(tPer).usuarioAsignado(analista)
                .estado(EstadoSubproceso.EN_EJECUCION).fechaCreacion(LocalDateTime.now()).versionPlantilla(tPer.getVersionPlantilla()).build());
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Las 4 referencias del candidato llegan precargadas, y el teléfono del candidato sale de su ficha")
    void precarga() {
        FormularioResponse formulario = formularioService.obtener(personal.getId(), analista);
        var declaradas = formulario.secciones().stream()
                .filter(s -> s.columnas().stream().anyMatch(c -> "rd_estado".equals(c.clave()))).findFirst().orElseThrow();
        assertThat(declaradas.filas()).hasSize(4);
        Map<String, Integer> id = idsPorClave(declaradas);
        assertThat(declaradas.filas()).extracting(f -> f.valores().get(id.get("rd_nombre")))
                .containsExactly("Ana Ejemplo", "Beto Ejemplo", "Carla Ejemplo", "Dario Ejemplo");
        assertThat(declaradas.filas()).extracting(f -> f.valores().get(id.get("rd_telefono"))).containsExactly(TELEFONOS);
        assertThat(declaradas.filas()).extracting(f -> f.valores().get(id.get("rd_parentesco"))).containsExactly("Amiga", "Vecino", "Colega", "Amigo");
        assertThat(declaradas.filas()).extracting(f -> f.valores().get(id.get("rd_tiempo"))).containsExactly("2 años", "3 años", "4 años", "5 años");
        assertThat(valorCampo(formulario, "ev_telefono")).isEqualTo("3100000000");
    }

    @Test
    @DisplayName("Guardar (1 y 3 validadas, 2 no contesta, la 3 contradice: PER-02) → Word con 4 declaradas sin teléfonos y 2 fichas → motor del estudio")
    void puntaAPunta() throws Exception {
        FormularioResponse formulario = formularioService.obtener(personal.getId(), analista);
        FormularioResponse guardado = formularioService.guardar(personal.getId(), caso(formulario), analista);

        assertThat(guardado.avisoCalculo()).isNull();
        assertThat(valorCampo(guardado, "srv_formula")).isEqualTo("PER-02: 40 de 100 posibles");
        assertThat(valorCampo(guardado, "srv_puntaje")).isEqualTo("40,0");
        assertThat(valorCampo(guardado, "sem_medio")).isEqualTo("▲ 40,0 puntos");
        assertThat(valorCampo(guardado, "per_promedio_general")).isEqualTo("4,8");
        assertThat(valorCampo(guardado, "per_validadas")).isEqualTo("2");

        em.flush();
        em.clear();
        ServicioSubproceso sp = em.find(ServicioSubproceso.class, personal.getId());
        sp.setEstado(EstadoSubproceso.FINALIZADO);
        sp.setFechaCompletado(LocalDateTime.now());
        em.flush();
        em.clear();

        byte[] word = documentoService.generar(personal.getId(), gestor);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(word));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String texto = extractor.getText();
            assertThat(texto).doesNotContain("${");

            // Sección 1: las 4 declaradas (título, encabezado y una fila por referencia), sin teléfonos ni estado.
            XWPFTable declaradas = tabla(doc, "REFERENCIAS PERSONALES DECLARADAS POR EL EVALUADO");
            assertThat(celdas(declaradas.getRow(1))).containsExactly("Nombre", "Parentesco o relación", "Tiempo de conocimiento");
            assertThat(List.of(celdas(declaradas.getRow(2)), celdas(declaradas.getRow(3)), celdas(declaradas.getRow(4)), celdas(declaradas.getRow(5))))
                    .containsExactly(List.of("Ana Ejemplo", "Amiga", "2 años"), List.of("Beto Ejemplo", "Vecino", "3 años"),
                            List.of("Carla Ejemplo", "Colega", "4 años"), List.of("Dario Ejemplo", "Amigo", "5 años"));
            for (String telefono : TELEFONOS) assertThat(texto).doesNotContain(telefono);
            assertThat(texto).doesNotContain("Descartada").doesNotContain("Llamó a la referencia");

            // Sección 3: PER-02 con 40.
            XWPFTable hallazgos = tabla(doc, "Ref.", "Hallazgo", "Fuente", "Detalle", "Puntos");
            assertThat(hallazgos.getRows()).hasSize(1 + 1 + 1);
            assertThat(celdas(hallazgos.getRow(1))).containsExactly("PER-02",
                    "La referencia da información que contradice lo verificado (trabajo, residencia)", "Referencia N° 2", "Carla Ejemplo (Colega)", "40");
            assertThat(texto).contains("40,0").contains("▲ 40,0 puntos");

            // Sección 5: una ficha por referencia validada.
            assertThat(texto).contains("REFERENCIA PERSONAL N° 1").contains("REFERENCIA PERSONAL N° 2").doesNotContain("REFERENCIA PERSONAL N° 3");
            assertThat(texto).contains("Carla Ejemplo").contains("Promedio general de las referencias (1 a 5):");
            assertThat(texto).doesNotContain("No fue posible validar referencias personales.");
            assertThat(texto).contains("Empresa Ficticia S.A.S.").contains("Auxiliar administrativo");
        }

        // El resultado llega al motor del estudio como servicio de peso 4.
        CalificacionRiesgo estudio = riesgoService.recalcular(servicio.getIdServicio(), gestor.getIdUsuario());
        assertThat(estudio.getEstado()).isEqualTo(EstadoResultado.CALCULADO);
        assertThat(estudio.getDetalleJson()).contains("\"nombre\":\"Validacion Personal\",\"estado\":\"REALIZADO\",\"peso\":4.00");
    }

    // ───────────────────────────── caso ─────────────────────────────

    private GuardarFormularioRequest caso(FormularioResponse f) {
        Map<String, String> campos = new HashMap<>(Map.of("ev_criticidad", "Estándar", "concepto", "Una referencia contradice lo verificado.",
                "analista_fecha", "22/10/2026"));
        String[] estados = {"Validada", "Descartada – no contesta", "Validada", "No contactada (no fue necesaria)"};
        List<Map<String, String>> validadas = List.of(
                Map.ofEntries(Map.entry("r_nombre", "Ana Ejemplo"), Map.entry("r_parentesco", "Amiga"), Map.entry("r_tiempo", "4 años"),
                        Map.entry("r_ultima", "Esta semana"), Map.entry("r_trabajo", "Sí, en una comercializadora"),
                        Map.entry("r_trabajo_coincide", "Coincide"), Map.entry("r_residencia", "Sí"), Map.entry("r_residencia_coincide", "Coincide"),
                        Map.entry("r_a1", "5"), Map.entry("r_a2", "5"), Map.entry("r_a3", "5"), Map.entry("r_a4", "4"), Map.entry("r_a5", "4"),
                        Map.entry("r_concepto", "Persona responsable y puntual."), Map.entry("r_ref", "—")),
                Map.ofEntries(Map.entry("r_nombre", "Carla Ejemplo"), Map.entry("r_parentesco", "Colega"), Map.entry("r_tiempo", "3 años"),
                        Map.entry("r_ultima", "Hace 15 días"), Map.entry("r_trabajo", "Dice que trabaja en otra empresa"),
                        Map.entry("r_trabajo_coincide", "No coincide"), Map.entry("r_residencia", "Sí"), Map.entry("r_residencia_coincide", "Coincide"),
                        Map.entry("r_a1", "5"), Map.entry("r_a2", "5"), Map.entry("r_a3", "5"), Map.entry("r_a4", "5"), Map.entry("r_a5", "5"),
                        Map.entry("r_concepto", "Buena persona; menciona otro empleador."), Map.entry("r_ref", "PER-02")));
        Map<String, List<Map<String, String>>> tablas = Map.of(
                "referencias", validadas,
                "gestiones", List.of(Map.of("g_referencia", "2", "g_fecha", "20/10/2026", "g_medio", "Llamada",
                        "g_resultado", "Llamó a la referencia y no contestó")));
        List<SeccionDatosRequest> secciones = new ArrayList<>();
        for (FormularioResponse.SeccionDatosResponse s : f.secciones()) {
            Map<String, Integer> idPorClave = idsPorClave(s);
            if ("CAMPOS".equals(s.tipo())) {
                Map<Integer, String> valores = new HashMap<>(s.valores());
                campos.forEach((clave, valor) -> { if (idPorClave.containsKey(clave)) valores.put(idPorClave.get(clave), valor); });
                secciones.add(new SeccionDatosRequest(s.idSeccion(), valores, null));
            } else if (idPorClave.containsKey("rd_estado")) {
                // Las 4 filas que llegaron precargadas, con el estado que les puso el analista.
                List<FilaDatosRequest> filas = new ArrayList<>();
                for (int i = 0; i < s.filas().size(); i++) {
                    Map<Integer, String> valores = new HashMap<>(s.filas().get(i).valores());
                    valores.put(idPorClave.get("rd_estado"), estados[i]);
                    filas.add(new FilaDatosRequest("", valores, null));
                }
                secciones.add(new SeccionDatosRequest(s.idSeccion(), null, filas));
            } else {
                String clave = idPorClave.containsKey("r_nombre") ? "referencias" : idPorClave.containsKey("g_fecha") ? "gestiones" : null;
                List<FilaDatosRequest> filas = new ArrayList<>();
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

    private static Map<String, Integer> idsPorClave(FormularioResponse.SeccionDatosResponse s) {
        Map<String, Integer> id = new HashMap<>();
        for (ColumnaResponse c : s.columnas()) id.put(c.clave(), c.idColumna());
        return id;
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
