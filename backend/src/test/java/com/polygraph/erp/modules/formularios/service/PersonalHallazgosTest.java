package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.formularios.entity.FormularioColumna;
import com.polygraph.erp.modules.formularios.entity.FormularioSeccion;
import com.polygraph.erp.modules.formularios.repository.FormularioSeccionRepository;
import com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CalculoFormulario;
import com.polygraph.erp.modules.riesgo.hallazgos.MotorHallazgos;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo;
import com.polygraph.erp.modules.riesgo.repository.CodigoCalificacionRepository;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoSeccionFormulario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/** Validación personal (V219) contra docs/Validacion Personal: las claves del formulario son letra por letra los
 *  marcadores del Word y los casos P1–P6 de motor_per.py dan lo mismo con la plantilla REAL y el catálogo REAL.
 *  Si una prueba no da, se corrige el motor o la plantilla, no el valor esperado. Datos inventados. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("Validación personal = motor_per.py y Word")
class PersonalHallazgosTest {

    static final Path WORD = Path.of("../docs/Validacion Personal/1_Formato_Referencias_Personales.docx");
    private static final Path V219 = Path.of("src/main/resources/db/migration/V219__plantilla_validacion_personal.sql");
    private static final String NO_CUMPLE = "Descartada – no cumple requisitos";
    private static final String NO_FUE_NECESARIA = "No contactada (no fue necesaria)";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private FormularioSeccionRepository seccionRepository;
    @Autowired private CodigoCalificacionRepository codigoRepository;

    private List<FormularioSeccion> secciones;
    private CalculoSubservicioService calculo;

    @BeforeEach
    void preparar() throws Exception {
        TipoProgreso t = TipoProgreso.builder().nombreProgreso("Validacion Personal").activo(true)
                .rolResponsable(Rol.ANALISTA_INTERNO).requiereDocumento(true).versionPlantilla(7).build();
        em.persist(t);
        em.flush();
        jdbc.execute(PlantillaAntecedentesPrueba.insertDeV202());
        ejecutar(jdbc);
        em.clear();
        secciones = seccionRepository.findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(t.getIdTipoProgreso());
        var catalogo = CalculoSubservicioService.catalogoDe(codigoRepository.findAll());
        calculo = new CalculoSubservicioService(() -> catalogo, () -> PlantillaAntecedentesPrueba.UMBRALES);
    }

    static void ejecutar(JdbcTemplate jdbc) throws Exception {
        String sql = Files.readAllLines(V219, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.trim().startsWith("--")).collect(Collectors.joining("\n"));
        for (String sentencia : sql.split(";\\s*\\n")) {
            if (!sentencia.isBlank()) jdbc.execute(sentencia.replaceAll(";\\s*$", ""));
        }
    }

    // ─────────────────────────── datos de los casos (CASOS de motor_per.py) ───────────────────────────

    private static Map<String, String> campos(String criticidad) {
        return new HashMap<>(Map.of("ev_criticidad", criticidad, "ev_telefono", "3100000000"));
    }

    /** R() de motor_per.py: una referencia validada. */
    private static Map<String, String> r(String ref, int... escala) {
        int[] e = escala.length == 0 ? new int[]{5, 5, 5, 5, 5} : escala;
        Map<String, String> m = new LinkedHashMap<>();
        m.put("r_nombre", "Referencia de ejemplo");
        m.put("r_parentesco", "Amigo");
        m.put("r_ref", ref);
        m.put("r_trabajo_coincide", "Coincide");
        m.put("r_residencia_coincide", "Coincide");
        for (int i = 0; i < 5; i++) m.put("r_a" + (i + 1), String.valueOf(e[i]));
        return m;
    }

    private static Map<String, String> r() { return r("—"); }

    /** D() de motor_per.py: una referencia declarada. */
    private static Map<String, String> d(String tel, String estado) {
        return new HashMap<>(Map.of("rd_nombre", "Declarada", "rd_parentesco", "Amigo", "rd_tiempo", "5 años", "rd_telefono", tel, "rd_estado", estado));
    }

    private static Map<String, String> d(String tel) { return d(tel, "Validada"); }

    private static List<Map<String, String>> ok4() {
        return List.of(d("3000000001"), d("3000000002"), d("3000000003", NO_FUE_NECESARIA), d("3000000004", NO_FUE_NECESARIA));
    }

    private CalculoFormulario calcular(Map<String, String> f, List<Map<String, String>> validadas, List<Map<String, String>> declaradas) {
        Map<String, List<Map<String, String>>> tablas = new HashMap<>();
        tablas.put("referencias", validadas.stream().<Map<String, String>>map(HashMap::new).collect(Collectors.toList()));
        tablas.put("referencias_declaradas", declaradas);
        return calculo.calcular(secciones, null, f, tablas).orElseThrow();
    }

    // ─────────────────────────── estructura ───────────────────────────

    @Test
    @DisplayName("La plantilla nace en la versión 1, con 8 secciones agrupadas y la calificación por hallazgos")
    void estructura() {
        assertThat(jdbc.queryForObject("SELECT version_plantilla FROM tipos_progreso WHERE nombre_progreso = 'Validacion Personal'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tipos_progreso WHERE nombre_progreso = 'Validacion Personal'", Integer.class)).isEqualTo(1);
        assertThat(secciones).extracting(FormularioSeccion::getClave).containsExactly("datos_evaluado", "resultado_servicio", "hallazgos",
                "informativos", "referencias", "referencias_declaradas", "gestiones", "concepto_analista");
        assertThat(secciones).extracting(FormularioSeccion::getGrupo).doesNotContainNull();
        assertThat(secciones).allSatisfy(s -> assertThat(s.getVersionCreacion()).isEqualTo(1));
        assertThat(MotorHallazgos.califica(CalculoSubservicioService.plantilla(secciones))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM codigos_calificacion WHERE codigo LIKE 'PER-%' OR codigo = 'ALR-PER-01'", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM codigos_calificacion WHERE codigo LIKE 'PER-%' AND critico = TRUE", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Las claves del formulario son los marcadores del Word (menos lo que no se imprime)")
    void clavesIgualesAlWord() throws Exception {
        Set<String> campos = new HashSet<>(), tablas = new HashSet<>(), columnasTabla = new HashSet<>();
        for (FormularioSeccion s : secciones) {
            var claves = s.getColumnas().stream().filter(c -> Boolean.TRUE.equals(c.getActivo())).map(FormularioColumna::getClave).toList();
            if (s.getTipo() == TipoSeccionFormulario.CAMPOS) campos.addAll(claves);
            else { tablas.add(s.getClave()); columnasTabla.addAll(claves); }
        }
        String texto = (leer("word/document.xml") + leer("word/header1.xml") + leer("word/footer1.xml")).replaceAll("<[^>]+>", "");
        Set<String> deCampos = marcadores(texto, "\\$\\{campos\\['([a-z0-9_]+)'\\]\\}");
        Set<String> deColumnas = marcadores(texto, "\\$\\{\\['([a-z0-9_]+)'\\]\\}");
        Set<String> deTablas = marcadores(leer("word/comments.xml").replaceAll("<[^>]+>", ""), "tablas\\['([a-z_]+)'\\]");

        assertThat(campos).containsAll(deCampos);
        assertThat(tablas).containsAll(deTablas);
        assertThat(columnasTabla).containsAll(deColumnas);
        // Lo que se diligencia o calcula y NO tiene marcador en el Word (no se imprime).
        Set<String> sinMarcador = new TreeSet<>(campos);
        sinMarcador.removeAll(deCampos);
        assertThat(sinMarcador).containsExactly("ev_criticidad", "ev_telefono", "per_validadas");
        Set<String> columnasSinMarcador = new TreeSet<>(columnasTabla);
        columnasSinMarcador.removeAll(deColumnas);
        assertThat(columnasSinMarcador).containsExactly("g_fecha", "g_medio", "g_referencia", "g_resultado", "r_ref", "rd_estado", "rd_telefono");
        // Las 4 funciones de repetición del Word.
        assertThat(deTablas).containsExactlyInAnyOrder("hallazgos", "informativos", "referencias", "referencias_declaradas");
    }

    @Test
    @DisplayName("El teléfono del candidato sale solo de su ficha y las referencias declaradas nacen de las que registró")
    void telefonoYPrecarga() {
        assertThat(jdbc.queryForObject("SELECT campo_candidato FROM formulario_columnas WHERE clave = 'ev_telefono'", String.class)).isEqualTo("CELULAR");
        var precarga = CandidatoInfoService.PRECARGAS.get("referencias_declaradas");
        assertThat(precarga.tablaFija()).isEqualTo("referencias");
        assertThat(precarga.columnas()).containsEntry("rd_nombre", "nombre").containsEntry("rd_telefono", "telefono")
                .containsEntry("rd_tiempo", "tiempo_conocimiento").containsEntry("rd_parentesco", "parentesco");
        Set<String> clavesFijas = new HashSet<>(CandidatoInfoService.COLUMNAS_TABLAS_FIJAS.get("referencias"));
        assertThat(clavesFijas).containsAll(precarga.columnas().values());
    }

    // ─────────────────────────── casos de motor_per.py ───────────────────────────

    @Test
    @DisplayName("P1 Dos referencias favorables (Estándar): sin hallazgos, 0,0 BAJO")
    void p1() {
        CalculoFormulario c = calcular(campos("Estándar"), List.of(r(), r()), ok4());
        assertThat(c.resultado().formula()).isEqualTo("Sin hallazgos que sumen puntos");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("0,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.BAJO);
        assertThat(c.campos()).containsEntry("per_promedio_general", "5,0").containsEntry("ref_nota", "").containsEntry("per_validadas", "2")
                .containsEntry("sem_bajo", "▲ 0,0 puntos").containsEntry("sem_medio", "").containsEntry("sem_alto", "");
        assertThat(c.tablas().get("referencias")).extracting(f -> f.get("r_numero") + "|" + f.get("r_promedio") + "|" + f.get("r_calificacion"))
                .containsExactly("1|5,0|—", "2|5,0|—");
        assertThat(c.tablas().get("hallazgos")).isEmpty();
    }

    @Test
    @DisplayName("P2 Una referencia con promedio 2,6 (Estándar): PER-03 automático, 50,0 MEDIO")
    void p2() {
        CalculoFormulario c = calcular(campos("Estándar"), List.of(r("—", 3, 2, 3, 2, 3), r()), ok4());
        assertThat(c.resultado().formula()).isEqualTo("PER-03: 50 de 100 posibles");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("50,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.MEDIO);
        assertThat(c.tablas().get("hallazgos")).hasSize(1);
        assertThat(c.tablas().get("hallazgos").get(0)).containsEntry("h_ref", "PER-03").containsEntry("h_puntos", "50")
                .containsEntry("h_fuente", "Referencia N° 1").containsEntry("h_detalle", "Referencia de ejemplo (Amigo)");
        assertThat(c.tablas().get("referencias")).extracting(f -> f.get("r_promedio") + "|" + f.get("r_calificacion"))
                .containsExactly("2,6|PER-03", "5,0|—");
        assertThat(c.campos()).containsEntry("per_promedio_general", "3,8");
    }

    @Test
    @DisplayName("P3 Referencia falsa + otra descartada por requisitos (Estándar): 70 + 20 = 90, ALTO")
    void p3() {
        var declaradas = List.of(d("3000000001", NO_CUMPLE), d("3000000002"), d("3000000003"), d("3000000004", NO_FUE_NECESARIA));
        CalculoFormulario c = calcular(campos("Estándar"), List.of(r("PER-01"), r()), declaradas);
        assertThat(c.resultado().formula()).isEqualTo("70 + 20 = 90 de 100 posibles");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("90,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(c.tablas().get("hallazgos")).extracting(f -> f.get("h_ref")).containsExactly("PER-01", "PER-06");
        assertThat(c.tablas().get("hallazgos").get(1)).containsEntry("h_fuente", "Referencias declaradas")
                .containsEntry("h_detalle", "1 referencia(s) descartada(s) por no cumplir los requisitos (cuenta una vez)");
        assertThat(c.tablas().get("referencias").get(0)).containsEntry("r_calificacion", "PER-01");
    }

    @Test
    @DisplayName("P4 Solo una referencia validada (Sensible): PER-05, 45,0 MEDIO")
    void p4() {
        var declaradas = List.of(d("3000000001"), d("3000000002", "Descartada – no contesta"), d("3000000003", "Descartada – no contesta"),
                d("3000000004", "Descartada – número errado no corregido"));
        CalculoFormulario c = calcular(campos("Sensible"), List.of(r()), declaradas);
        assertThat(c.resultado().formula()).isEqualTo("PER-05: 45 de 100 posibles");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("45,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.MEDIO);
        assertThat(c.tablas().get("hallazgos")).hasSize(1);
        assertThat(c.tablas().get("hallazgos").get(0)).containsEntry("h_ref", "PER-05").containsEntry("h_fuente", "Referencias personales")
                .containsEntry("h_detalle", "1 de 2 referencias validadas");
        assertThat(c.campos()).containsEntry("per_validadas", "1");
    }

    @Test
    @DisplayName("P5 Ambas contradicen lo verificado (Crítico): PER-02 + PER-02 = 80, ALTO")
    void p5() {
        Map<String, String> primera = r("PER-02");
        primera.put("r_trabajo_coincide", "No coincide");
        Map<String, String> segunda = r("PER-02");
        segunda.put("r_residencia_coincide", "No coincide");
        CalculoFormulario c = calcular(campos("Crítico"), List.of(primera, segunda), ok4());
        assertThat(c.resultado().formula()).isEqualTo("40 + 40 = 80 de 100 posibles");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("80,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(c.tablas().get("hallazgos")).extracting(f -> f.get("h_ref") + "|" + f.get("h_fuente")).containsExactly("PER-02|Referencia N° 1", "PER-02|Referencia N° 2");
    }

    @Test
    @DisplayName("P6 Teléfonos repetidos al registrar, referencias favorables: no suman puntos")
    void p6() {
        var declaradas = List.of(d("3183504618"), d("3183504618"), d("31835046"), d("3100000000"));
        CalculoFormulario c = calcular(campos("Estándar"), List.of(r(), r()), declaradas);
        assertThat(c.resultado().formula()).isEqualTo("Sin hallazgos que sumen puntos");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("0,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.BAJO);
    }

    // ─────────────────────────── reglas propias ───────────────────────────

    @Test
    @DisplayName("PER-03 puesto por el analista y por el promedio a la vez cuenta una sola vez en esa referencia")
    void per03SinRepetir() {
        CalculoFormulario c = calcular(campos("Estándar"), List.of(r("PER-03", 2, 2, 2, 2, 2), r()), ok4());
        assertThat(c.tablas().get("hallazgos")).extracting(f -> f.get("h_ref")).containsExactly("PER-03");
        assertThat(c.tablas().get("referencias").get(0)).containsEntry("r_calificacion", "PER-03").containsEntry("r_promedio", "2,0");
    }

    @Test
    @DisplayName("Promedio 3,0 no es menor a 3: sin PER-03")
    void promedioTresNoSuma() {
        CalculoFormulario c = calcular(campos("Estándar"), List.of(r("—", 3, 3, 3, 3, 3), r()), ok4());
        assertThat(c.resultado().puntajeTexto()).isEqualTo("0,0");
    }

    @Test
    @DisplayName("Sin referencias validadas: PER-05, nota 'No fue posible validar…' y promedio general en '—'")
    void sinReferencias() {
        CalculoFormulario c = calcular(campos("Estándar"), List.of(), ok4());
        assertThat(c.resultado().formula()).isEqualTo("PER-05: 45 de 100 posibles");
        assertThat(c.campos()).containsEntry("ref_nota", "No fue posible validar referencias personales.")
                .containsEntry("per_promedio_general", "—").containsEntry("per_validadas", "0");
        assertThat(c.tablas().get("hallazgos").get(0)).containsEntry("h_detalle", "0 de 2 referencias validadas");
    }

    @Test
    @DisplayName("Dos referencias falsas + sin 2.ª validada + descartada por requisitos: 70 + 70 + 20 = 160 → 100 (tope)")
    void tope() {
        var declaradas = List.of(d("3000000001", NO_CUMPLE), d("3000000002", NO_CUMPLE), d("3000000003"), d("3000000004"));
        CalculoFormulario c = calcular(campos("Estándar"), List.of(r("PER-01"), r("PER-01")), declaradas);
        assertThat(c.resultado().formula()).isEqualTo("70 + 70 + 20 = 160 → 100 (tope máximo)");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("100,0");
        assertThat(c.tablas().get("hallazgos").get(2)).containsEntry("h_ref", "PER-06")
                .containsEntry("h_detalle", "2 referencia(s) descartada(s) por no cumplir los requisitos (cuenta una vez)");
    }

    @Test
    @DisplayName("PER-05 antes que PER-06 (como motor_per.py): una validada y una descartada por requisitos = 45 + 20")
    void orden05y06() {
        CalculoFormulario c = calcular(campos("Estándar"), List.of(r()),
                List.of(d("3000000001", NO_CUMPLE), d("3000000002"), d("3000000003", NO_FUE_NECESARIA), d("3000000004", NO_FUE_NECESARIA)));
        assertThat(c.resultado().formula()).isEqualTo("45 + 20 = 65 de 100 posibles");
        assertThat(c.tablas().get("hallazgos")).extracting(f -> f.get("h_ref")).containsExactly("PER-05", "PER-06");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.ALTO);
    }

    @Test
    @DisplayName("Sin criticidad no se calcula nada y se avisa")
    void sinCriticidad() {
        CalculoFormulario c = calcular(new HashMap<>(), List.of(r(), r()), ok4());
        assertThat(c.aviso()).isEqualTo(MotorHallazgos.FALTA_CRITICIDAD);
        assertThat(c.resultado()).isNull();
    }

    // ─────────────────────────── apoyo ───────────────────────────

    private static String leer(String entrada) throws Exception {
        try (ZipFile zip = new ZipFile(WORD.toFile())) {
            ZipEntry e = zip.getEntry(entrada);
            return e == null ? "" : new String(zip.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Set<String> marcadores(String texto, String regex) {
        Set<String> r = new HashSet<>();
        Matcher m = Pattern.compile(regex).matcher(texto);
        while (m.find()) r.add(m.group(1));
        return r;
    }
}
