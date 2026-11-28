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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/** Validación laboral (V218) contra docs/Validacion Laboral: las claves del formulario son letra por letra los
 *  marcadores del Word y los casos L1–L10 de motor_lab.py dan lo mismo con la plantilla REAL y el catálogo REAL.
 *  Si una prueba no da, se corrige el motor o la plantilla, no el valor esperado. Datos inventados. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("Validación laboral = motor_lab.py y Word")
class LaboralHallazgosTest {

    private static final Path WORD = Path.of("../docs/Validacion Laboral/VERIFICACION_LABORAL_PLATAFORMA_FINAL.docx");
    private static final Path V218 = Path.of("src/main/resources/db/migration/V218__plantilla_validacion_laboral.sql");
    private static final String NOTA_SABANA = "Se confirma la vinculación por medio de bases de datos públicas y privadas. No fue posible validar "
            + "motivo de retiro, llamados de atención ni la veracidad del certificado.";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private FormularioSeccionRepository seccionRepository;
    @Autowired private CodigoCalificacionRepository codigoRepository;

    private List<FormularioSeccion> secciones;
    private CalculoSubservicioService calculo;

    @BeforeEach
    void preparar() throws Exception {
        TipoProgreso t = TipoProgreso.builder().nombreProgreso("Validacion Laboral").activo(true)
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
        String sql = Files.readAllLines(V218, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.trim().startsWith("--")).collect(Collectors.joining("\n"));
        for (String sentencia : sql.split(";\\s*\\n")) {
            if (!sentencia.isBlank()) jdbc.execute(sentencia.replaceAll(";\\s*$", ""));
        }
    }

    private static Map<String, String> campos(String criticidad) {
        return new HashMap<>(Map.of("ev_criticidad", criticidad, "lab_periodo_desde", "10/2022", "lab_periodo_hasta", "10/2026"));
    }

    private static Map<String, String> v(String r1, String r2, String por, String tipo) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("v_empresa", "Empresa de ejemplo");
        m.put("v_cargo", "Cargo de ejemplo");
        m.put("v_ref1", r1);
        m.put("v_ref2", r2);
        m.put("v_validado_por", por);
        m.put("v_tipo", tipo);
        return m;
    }

    private static Map<String, String> v(String r1) { return v(r1, "—", "La empresa", "Empresa"); }

    private static Map<String, String> in(String corroboracion) {
        return new HashMap<>(Map.of("in_corroboracion", corroboracion));
    }

    private CalculoFormulario calcular(Map<String, String> f, List<Map<String, String>> vinculaciones, List<Map<String, String>> inactividad) {
        Map<String, List<Map<String, String>>> tablas = new HashMap<>();
        tablas.put("vinculaciones", vinculaciones.stream().<Map<String, String>>map(HashMap::new).collect(Collectors.toList()));
        tablas.put("inactividad", inactividad);
        return calculo.calcular(secciones, null, f, tablas).orElseThrow();
    }

    @Test
    @DisplayName("La plantilla nace en la versión 1, con 11 secciones agrupadas y la calificación por hallazgos")
    void estructura() {
        assertThat(jdbc.queryForObject("SELECT version_plantilla FROM tipos_progreso WHERE nombre_progreso = 'Validacion Laboral'", Integer.class)).isEqualTo(1);
        assertThat(secciones).extracting(FormularioSeccion::getClave).containsExactly("datos_evaluado", "experiencia_laboral", "resultado_servicio",
                "hallazgos", "informativos", "alcance", "vinculaciones", "inactividad", "obs_inactividad", "gestiones", "concepto_analista");
        assertThat(secciones).extracting(FormularioSeccion::getGrupo).doesNotContainNull();
        assertThat(secciones).allSatisfy(s -> assertThat(s.getVersionCreacion()).isEqualTo(1));
        assertThat(MotorHallazgos.califica(CalculoSubservicioService.plantilla(secciones))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM codigos_calificacion WHERE codigo LIKE 'LAB-%' OR codigo = 'ALR-LAB-01'", Integer.class)).isEqualTo(15);
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
        Set<String> deLaExperiencia = Set.of("empresa", "cargo", "fecha_inicio", "fecha_fin"); // tabla fija de la hoja de vida
        assertThat(columnasTabla).containsAll(deColumnas.stream().filter(c -> !deLaExperiencia.contains(c)).collect(Collectors.toSet()));
        assertThat(deColumnas).containsAll(deLaExperiencia);
        Set<String> sinMarcador = new HashSet<>(campos);
        sinMarcador.removeAll(deCampos);
        assertThat(sinMarcador).containsExactly("ev_criticidad");
    }

    @Test
    @DisplayName("L1 Sin novedad (Estándar)")
    void l1() {
        CalculoFormulario c = calcular(campos("Estándar"), List.of(v("—"), v("—")), List.of());
        assertThat(c.resultado().formula()).isEqualTo("Sin hallazgos que sumen puntos");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("0,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.BAJO);
        assertThat(c.campos()).containsEntry("vinc_nota", "").containsEntry("inactividad_nota",
                "No se identificaron periodos de inactividad superiores a 30 días en el periodo validado.");
        assertThat(c.tablas().get("vinculaciones")).extracting(f -> f.get("v_numero") + "|" + f.get("v_calificacion") + "|" + f.get("v_nota"))
                .containsExactly("1|—|", "2|—|");
    }

    @Test
    @DisplayName("L2 Validada por sábana pensional: sin hallazgos y con la nota de alcance")
    void l2() {
        CalculoFormulario c = calcular(campos("Estándar"), List.of(v("—", "—", "Sábana pensional o bases de datos", "Empresa")), List.of());
        assertThat(c.resultado().puntajeTexto()).isEqualTo("0,0");
        assertThat(c.tablas().get("vinculaciones").get(0)).containsEntry("v_nota", NOTA_SABANA);
    }

    @Test
    @DisplayName("L3 Llamado de cumplimiento + motivo no coincide (Sensible): 50 + 45 = 95, ALTO")
    void l3() {
        CalculoFormulario c = calcular(campos("Sensible"), List.of(v("LAB-07"), v("LAB-04")), List.of());
        assertThat(c.resultado().formula()).isEqualTo("50 + 45 = 95 de 100 posibles");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("95,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(c.tablas().get("hallazgos")).extracting(f -> f.get("h_ref")).containsExactly("LAB-07", "LAB-04");
        assertThat(c.tablas().get("hallazgos").get(0)).containsEntry("h_fuente", "La empresa")
                .containsEntry("h_detalle", "Vinculación N° 1: Empresa de ejemplo (Cargo de ejemplo)");
        assertThat(c.tablas().get("vinculaciones")).extracting(f -> f.get("v_calificacion")).containsExactly("LAB-07", "LAB-04");
    }

    @Test
    @DisplayName("L4 Certificado falso (LAB-01, Crítico): 100,0 y ALTO")
    void l4() {
        CalculoFormulario c = calcular(campos("Crítico"), List.of(v("LAB-01")), List.of());
        assertThat(c.resultado().formula()).isEqualTo("Hallazgo crítico (LAB-01): nivel ALTO");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("100,0");
        assertThat(c.campos()).containsEntry("sem_alto", "▲ Hallazgo crítico");
    }

    @Test
    @DisplayName("L5 Datos no coinciden + 2 inactividades sin corroborar (cuentan una vez): 60 + 30 = 90")
    void l5() {
        CalculoFormulario c = calcular(campos("Estándar"), List.of(v("LAB-03")), List.of(in("No se pudo corroborar"), in("No se pudo corroborar")));
        assertThat(c.resultado().formula()).isEqualTo("60 + 30 = 90 de 100 posibles");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(c.tablas().get("hallazgos")).extracting(f -> f.get("h_ref")).containsExactly("LAB-03", "LAB-13");
        assertThat(c.tablas().get("hallazgos").get(1)).containsEntry("h_fuente", "Periodos de inactividad");
    }

    @Test
    @DisplayName("L6 Llamados en dos empresas, uno repetido en la misma: cuenta 50 + 50")
    void l6() {
        CalculoFormulario c = calcular(campos("Estándar"), List.of(v("LAB-07", "LAB-07", "La empresa", "Empresa"), v("LAB-07")), List.of());
        assertThat(c.resultado().formula()).isEqualTo("50 + 50 = 100 de 100 posibles");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("100,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(c.tablas().get("vinculaciones").get(0)).containsEntry("v_calificacion", "LAB-07");
    }

    @Test
    @DisplayName("L7 Despido con justa causa + abandono: 70 + 60 = 130 → 100 (tope máximo)")
    void l7() {
        CalculoFormulario c = calcular(campos("Estándar"), List.of(v("LAB-05"), v("LAB-06")), List.of());
        assertThat(c.resultado().formula()).isEqualTo("70 + 60 = 130 → 100 (tope máximo)");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("100,0");
    }

    @Test
    @DisplayName("L8 Concepto regular (LAB-12, Sensible): 'LAB-12: 30 de 100 posibles', MEDIO")
    void l8() {
        CalculoFormulario c = calcular(campos("Sensible"), List.of(v("LAB-12")), List.of());
        assertThat(c.resultado().formula()).isEqualTo("LAB-12: 30 de 100 posibles");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("30,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.MEDIO);
    }

    @Test
    @DisplayName("L9 Nunca ha trabajado formalmente, inactividad corroborada: sin hallazgos y con la nota de vinculaciones")
    void l9() {
        CalculoFormulario c = calcular(campos("Estándar"), List.of(), List.of(in("Referencia confirmó")));
        assertThat(c.resultado().puntajeTexto()).isEqualTo("0,0");
        assertThat(c.campos()).containsEntry("vinc_nota", "El evaluado no registra vinculaciones laborales en el periodo validado.")
                .containsEntry("inactividad_nota", "");
    }

    @Test
    @DisplayName("L10 Inactividad: una sin corroborar y dos solo declaradas: 30 + 10 = 40, MEDIO")
    void l10() {
        CalculoFormulario c = calcular(campos("Sensible"), List.of(v("—")),
                List.of(in("No se pudo corroborar"), in("Solo declaración del evaluado"), in("Solo declaración del evaluado")));
        assertThat(c.resultado().formula()).isEqualTo("30 + 10 = 40 de 100 posibles");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.MEDIO);
        assertThat(c.tablas().get("hallazgos")).extracting(f -> f.get("h_ref")).containsExactly("LAB-13", "LAB-14");
    }

    @Test
    @DisplayName("Una alerta (ALR-LAB-01) no suma ni genera hallazgo, pero aparece en la calificación de la vinculación")
    void alerta() {
        CalculoFormulario c = calcular(campos("Estándar"), List.of(v("ALR-LAB-01")), List.of());
        assertThat(c.resultado().puntajeTexto()).isEqualTo("0,0");
        assertThat(c.tablas().get("hallazgos")).isEmpty();
        assertThat(c.tablas().get("vinculaciones").get(0)).containsEntry("v_calificacion", "ALR-LAB-01");
    }

    @Test
    @DisplayName("LB-08: el Word del cliente no trae el catálogo (Anexo 1) y «Cómo leer este reporte» ya no lo nombra")
    void elWordDelClienteNoTraeElCatalogo() throws Exception {
        String texto = leer("word/document.xml").replaceAll("<[^>]+>", "");
        assertThat(texto).doesNotContain("Anexo 1").doesNotContain("CATÁLOGO DE REFERENCIAS").doesNotContain("la empresa lo desconoce, o la alteración es evidente");
        assertThat(texto).contains("un código del catálogo de Polygraph, que indica sus puntos");
        assertThat(texto).contains("Los soportes de cada consulta se entregan como anexos");
    }

    // ─────────── apoyo ───────────

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
