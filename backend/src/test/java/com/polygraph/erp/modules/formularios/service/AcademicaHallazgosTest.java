package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.formularios.entity.FormularioColumna;
import com.polygraph.erp.modules.formularios.entity.FormularioSeccion;
import com.polygraph.erp.modules.formularios.repository.FormularioSeccionRepository;
import com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CalculoFormulario;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.Hallazgo;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/** Validación académica (V216) contra docs/Validacion Academica: las claves del formulario son letra por
 *  letra los marcadores del Word y los casos A1–A9 de motor_aca.py dan lo mismo con la plantilla REAL y el
 *  catálogo REAL. Si una prueba no da, se corrige el motor o la plantilla, no el valor esperado. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("Validación académica = motor_aca.py y Word")
class AcademicaHallazgosTest {

    private static final Path WORD = Path.of("../docs/Validacion Academica/VERIFICACION_ACADEMICA_PLATAFORMA_FINAL.docx");
    private static final Path V216 = Path.of("src/main/resources/db/migration/V216__plantilla_validacion_academica.sql");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private FormularioSeccionRepository seccionRepository;
    @Autowired private CodigoCalificacionRepository codigoRepository;

    private List<FormularioSeccion> secciones;
    private CalculoSubservicioService calculo;

    @BeforeEach
    void preparar() throws Exception {
        TipoProgreso t = TipoProgreso.builder().nombreProgreso("Validacion Academica").activo(true)
                .rolResponsable(Rol.ANALISTA_INTERNO).requiereDocumento(true).versionPlantilla(23).build();
        em.persist(t);
        em.flush();
        jdbc.execute(PlantillaAntecedentesPrueba.insertDeV202());
        String sql = Files.readAllLines(V216, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.trim().startsWith("--")).collect(Collectors.joining("\n"));
        for (String sentencia : sql.split(";\\s*\\n")) {
            if (!sentencia.isBlank()) jdbc.execute(sentencia.replaceAll(";\\s*$", ""));
        }
        em.clear();
        secciones = seccionRepository.findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(t.getIdTipoProgreso());
        var catalogo = CalculoSubservicioService.catalogoDe(codigoRepository.findAll());
        calculo = new CalculoSubservicioService(() -> catalogo, () -> PlantillaAntecedentesPrueba.UMBRALES);
    }

    private static Map<String, String> base(String criticidad, String ref) {
        Map<String, String> f = new HashMap<>();
        f.put("ev_criticidad", criticidad);
        f.put("aca_ref", ref);
        f.put("aca_institucion", "Institución de ejemplo");
        f.put("aca_titulo", "Título de ejemplo");
        f.put("aca_medio", "Base de datos de la institución");
        f.put("aca_criterio", "Último estudio finalizado");
        return f;
    }

    private CalculoFormulario calcular(Map<String, String> f) {
        return calculo.calcular(secciones, null, f, Map.of()).orElseThrow();
    }

    @Test
    @DisplayName("La plantilla nace en la versión 1, con las 8 secciones y la calificación por hallazgos")
    void estructura() {
        assertThat(jdbc.queryForObject("SELECT version_plantilla FROM tipos_progreso WHERE nombre_progreso = 'Validacion Academica'", Integer.class))
                .isEqualTo(1);
        assertThat(secciones).extracting(FormularioSeccion::getClave).containsExactly("datos_evaluado", "educacion", "resultado_servicio",
                "hallazgos", "informativos", "estudio_verificado", "concepto_analista", "gestiones");
        assertThat(secciones).allSatisfy(s -> assertThat(s.getVersionCreacion()).isEqualTo(1));
        assertThat(MotorHallazgos.califica(CalculoSubservicioService.plantilla(secciones))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM codigos_calificacion WHERE codigo LIKE '%ACA-%'", Integer.class)).isEqualTo(7);
    }

    @Test
    @DisplayName("Las claves del formulario son los marcadores del Word (menos lo que no se imprime)")
    void clavesIgualesAlWord() throws Exception {
        Set<String> campos = new HashSet<>(), tablas = new HashSet<>(), columnasTabla = new HashSet<>();
        for (FormularioSeccion s : secciones) {
            if (s.getTipo() == TipoSeccionFormulario.CAMPOS) {
                s.getColumnas().stream().filter(c -> Boolean.TRUE.equals(c.getActivo())).map(FormularioColumna::getClave).forEach(campos::add);
            } else {
                tablas.add(s.getClave());
                s.getColumnas().stream().filter(c -> Boolean.TRUE.equals(c.getActivo())).map(FormularioColumna::getClave).forEach(columnasTabla::add);
            }
        }
        String xml = leer("word/document.xml") + leer("word/header1.xml") + leer("word/footer1.xml");
        String texto = xml.replaceAll("<[^>]+>", "");
        Set<String> deCampos = marcadores(texto, "\\$\\{campos\\['([a-z0-9_]+)'\\]\\}");
        Set<String> deColumnas = marcadores(texto, "\\$\\{\\['([a-z0-9_]+)'\\]\\}");
        Set<String> deFicha = marcadores(texto, "\\$\\{candidato\\['([A-Z_]+)'\\]\\}");
        Set<String> deTablas = marcadores(leer("word/comments.xml").replaceAll("<[^>]+>", ""), "tablas\\['([a-z_]+)'\\]");

        // Todo marcador del Word existe en el formulario...
        assertThat(campos).containsAll(deCampos);
        assertThat(deFicha).isSubsetOf("NOMBRE_COMPLETO", "TIPO_DOCUMENTO", "CEDULA", "FECHA_EXPEDICION", "FECHA_NACIMIENTO", "LUGAR_NACIMIENTO");
        assertThat(tablas).containsAll(deTablas);
        Set<String> deLaEducacion = Set.of("nivel", "institucion", "titulo", "fecha_fin"); // tabla fija de la hoja de vida
        assertThat(columnasTabla).containsAll(deColumnas.stream().filter(c -> !deLaEducacion.contains(c)).collect(Collectors.toSet()));
        assertThat(deColumnas).containsAll(deLaEducacion);
        // ...y lo único del formulario que el Word no imprime es lo que el diccionario dice que no se imprime.
        Set<String> sinMarcador = new HashSet<>(campos);
        sinMarcador.removeAll(deCampos);
        assertThat(sinMarcador).containsExactlyInAnyOrder("ev_criticidad", "aca_ac01_base", "aca_fecha_sol_eval", "aca_fecha_limite");
        Set<String> columnasSinMarcador = new HashSet<>(columnasTabla);
        columnasSinMarcador.removeAll(deColumnas);
        assertThat(columnasSinMarcador).containsExactlyInAnyOrder("g_fecha", "g_medio", "g_destino", "g_resultado");
    }

    @Test
    @DisplayName("A1 Sin novedad (Estándar)")
    void a1() {
        CalculoFormulario c = calcular(base("Estándar", "—"));
        assertThat(c.resultado().formula()).isEqualTo("Sin hallazgos que sumen puntos");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("0,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.BAJO);
        assertThat(c.tablas().get("hallazgos")).isEmpty();
        assertThat(c.campos()).containsEntry("sem_bajo", "▲ 0,0 puntos").containsEntry("sem_medio", "").containsEntry("sem_alto", "");
    }

    @Test
    @DisplayName("A2 Documento falso (ACA-01): crítico, 100,0 y ALTO")
    void a2() {
        CalculoFormulario c = calcular(base("Estándar", "ACA-01"));
        assertThat(c.resultado().formula()).isEqualTo("Hallazgo crítico (ACA-01): nivel ALTO");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("100,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(c.resultado().hallazgos()).containsExactly(new Hallazgo("ACA-01", "Documento falso o adulterado",
                "Base de datos de la institución", "Título de ejemplo – Institución de ejemplo", "Crítico"));
        assertThat(c.campos()).containsEntry("sem_alto", "▲ Hallazgo crítico").containsEntry("sem_bajo", "").containsEntry("sem_medio", "");
    }

    @Test
    @DisplayName("A3 La institución no confirma el título (ACA-02): 70 y ALTO")
    void a3() {
        CalculoFormulario c = calcular(base("Estándar", "ACA-02"));
        assertThat(c.resultado().formula()).isEqualTo("ACA-02: 70 de 100 posibles");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("70,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.ALTO);
    }

    @Test
    @DisplayName("A4 Datos que no coinciden (ACA-03): 45 y MEDIO, con la fila de la sección 3 y el semáforo")
    void a4() {
        CalculoFormulario c = calcular(base("Estándar", "ACA-03"));
        assertThat(c.resultado().formula()).isEqualTo("ACA-03: 45 de 100 posibles");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("45,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.MEDIO);
        assertThat(c.tablas().get("hallazgos")).containsExactly(Map.of("h_ref", "ACA-03",
                "h_descripcion", "Datos que no coinciden con lo declarado (diferencia de fondo)",
                "h_fuente", "Base de datos de la institución", "h_detalle", "Título de ejemplo – Institución de ejemplo", "h_puntos", "45"));
        assertThat(c.campos()).containsEntry("sem_medio", "▲ 45,0 puntos").containsEntry("srv_puntaje", "45,0");
    }

    @Test
    @DisplayName("A5 No aportó lo solicitado (ACA-04) en cargo Crítico: 45 > 40 → ALTO")
    void a5() {
        CalculoFormulario c = calcular(base("Crítico", "ACA-04"));
        assertThat(c.resultado().formula()).isEqualTo("ACA-04: 45 de 100 posibles");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.ALTO);
    }

    @Test
    @DisplayName("A6 Institución no reconocida (ACA-05): 30 y BAJO en Estándar; 30 > 25 → MEDIO en Sensible")
    void a6() {
        CalculoFormulario estandar = calcular(base("Estándar", "ACA-05"));
        assertThat(estandar.resultado().formula()).isEqualTo("ACA-05: 30 de 100 posibles");
        assertThat(estandar.resultado().nivel()).isEqualTo(NivelRiesgo.BAJO);
        assertThat(calcular(base("Sensible", "ACA-05")).resultado().nivel()).isEqualTo(NivelRiesgo.MEDIO);
    }

    @Test
    @DisplayName("A7 La institución no responde (ALR-ACA-02): alerta, no suma ni genera hallazgo")
    void a7() {
        CalculoFormulario c = calcular(base("Sensible", "ALR-ACA-02"));
        assertThat(c.resultado().formula()).isEqualTo("Sin hallazgos que sumen puntos");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("0,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.BAJO);
        assertThat(c.tablas().get("hallazgos")).isEmpty();
    }

    @Test
    @DisplayName("A8 Por solicitud del cliente (ACA-03, Sensible): 45 y MEDIO")
    void a8() {
        Map<String, String> f = base("Sensible", "ACA-03");
        f.put("aca_criterio", "Por solicitud del cliente");
        CalculoFormulario c = calcular(f);
        assertThat(c.resultado().puntajeTexto()).isEqualTo("45,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.MEDIO);
    }

    @Test
    @DisplayName("A9 Terminó materias y sin código: 0,0 y BAJO")
    void a9() {
        Map<String, String> f = base("Estándar", "—");
        f.put("aca_estado", "Terminó materias, pendiente de grado");
        CalculoFormulario c = calcular(f);
        assertThat(c.resultado().puntajeTexto()).isEqualTo("0,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.BAJO);
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
