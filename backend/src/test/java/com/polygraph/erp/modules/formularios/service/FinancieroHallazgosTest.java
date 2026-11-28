package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.formularios.entity.FormularioColumna;
import com.polygraph.erp.modules.formularios.entity.FormularioSeccion;
import com.polygraph.erp.modules.formularios.repository.FormularioSeccionRepository;
import com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CalculoFormulario;
import com.polygraph.erp.modules.riesgo.financiero.MotorFinanciero;
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

/** Análisis financiero (V221) contra docs/Analisis Financiero: las claves del formulario son letra por letra los marcadores
 *  del Word y los casos F1–F9 de motor_fin.py dan lo mismo con la plantilla REAL y el catálogo REAL.
 *  Si una prueba no da, se corrige el motor o la plantilla, no el valor esperado. Datos inventados. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("Análisis financiero = motor_fin.py y Word")
class FinancieroHallazgosTest {

    static final Path WORD = Path.of("../docs/Analisis Financiero/1_Formato_Financiero.docx");
    private static final Path V221 = Path.of("src/main/resources/db/migration/V221__plantilla_analisis_financiero.sql");
    static final String OMITIO = "No coincide – omitió u ocultó (FIN-08)";
    private static final String SALVEDAD = "Alcance: la consulta de síntesis muestra los totales por sector y el comportamiento de pago de los últimos "
            + "12 meses. No identifica la entidad de cada obligación, la cartera castigada ni las obligaciones como codeudor en mora.";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private FormularioSeccionRepository seccionRepository;
    @Autowired private CodigoCalificacionRepository codigoRepository;

    private List<FormularioSeccion> secciones;
    private CalculoSubservicioService calculo;

    @BeforeEach
    void preparar() throws Exception {
        TipoProgreso t = TipoProgreso.builder().nombreProgreso("Analisis Financiero").activo(true)
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
        String sql = Files.readAllLines(V221, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.trim().startsWith("--")).collect(Collectors.joining("\n"));
        for (String sentencia : sql.split(";\\s*\\n")) {
            if (!sentencia.isBlank()) jdbc.execute(sentencia.replaceAll(";\\s*$", ""));
        }
    }

    // ─────────────────────────── datos de los casos (S, F, OB, PT de motor_fin.py) ───────────────────────────

    private static final String[] COLS = {"vig", "cer", "pri", "cod", "cuota", "mora"};

    private static void sector(Map<String, String> f, String k, int... v) {
        for (int i = 0; i < COLS.length; i++) f.put("s_" + k + "_" + COLS[i], String.valueOf(v[i]));
    }

    /** F() de motor_fin.py con los sectores por defecto. */
    static Map<String, String> f(String crit, int m90, int m3060, int pct, int score) {
        Map<String, String> d = new HashMap<>();
        d.put("ev_criticidad", crit);
        d.put("fin_meses_mora_90", String.valueOf(m90));
        d.put("fin_meses_mora_30_60", String.valueOf(m3060));
        d.put("fin_ingreso", "3000");
        d.put("fin_endeudamiento", String.valueOf(pct));
        d.put("cr_score", String.valueOf(score));
        for (String k : new String[]{"ing", "deu", "mor", "bie"}) d.put("c_" + k + "_res", "Coincide");
        sector(d, "fin", 1, 3, 4, 0, 500, 0);
        sector(d, "real", 0, 0, 0, 0, 0, 0);
        sector(d, "coop", 0, 0, 0, 0, 0, 0);
        sector(d, "tel", 1, 2, 3, 0, 80, 0);
        return d;
    }

    private static Map<String, String> f(String crit) { return f(crit, 0, 0, 25, 780); }

    static Map<String, String> ob(String entidad, String estado, int dias, int valor, String calidad, String tipo) {
        return new HashMap<>(Map.of("o_entidad", entidad, "o_tipo", tipo, "o_calidad", calidad, "o_estado", estado,
                "o_dias", String.valueOf(dias), "o_valor", String.valueOf(valor)));
    }

    static Map<String, String> pt(String tipo, String estado, String explicacion) {
        return new HashMap<>(Map.of("pt_tipo", tipo, "pt_ubicacion", "Ubicación de ejemplo", "pt_matricula", "000-000",
                "pt_estado", estado, "pt_explicacion", explicacion));
    }

    private CalculoFormulario calcular(Map<String, String> f, boolean oea, List<Map<String, String>> obligaciones, List<Map<String, String>> patrimonios) {
        Map<String, List<Map<String, String>>> tablas = new HashMap<>();
        tablas.put("obligaciones_negativas", obligaciones);
        tablas.put("patrimonios", patrimonios);
        tablas.put("informativos_analista", List.of());
        return calculo.calcular(secciones, null, Map.of("es_oea", oea ? "Sí" : "No"), f, tablas).orElseThrow();
    }

    private CalculoFormulario calcular(Map<String, String> f) { return calcular(f, false, List.of(), List.of()); }

    private static List<String> codigos(CalculoFormulario c) {
        return c.tablas().get("hallazgos").stream().map(h -> h.get("h_ref")).toList();
    }

    private static List<String> avisos(CalculoFormulario c) {
        return c.avisos().stream().map(a -> a.texto()).toList();
    }

    // ─────────────────────────── estructura ───────────────────────────

    @Test
    @DisplayName("La plantilla nace en la versión 1, con sus secciones agrupadas, las de OEA opcionales y la calificación activa")
    void estructura() {
        assertThat(jdbc.queryForObject("SELECT version_plantilla FROM tipos_progreso WHERE nombre_progreso = 'Analisis Financiero'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tipos_progreso WHERE nombre_progreso = 'Analisis Financiero'", Integer.class)).isEqualTo(1);
        assertThat(secciones).extracting(FormularioSeccion::getClave).containsExactly("datos_evaluado", "resultado_servicio", "hallazgos",
                "informativos", "informativos_analista", "consulta_sintesis", "analisis_ingresos", "consolidado", "alertas_internas",
                "obligaciones_negativas", "patrimonios", "oea_datos", "oea", "concepto_analista", "adjuntos");
        assertThat(secciones).extracting(FormularioSeccion::getGrupo).doesNotContainNull();
        assertThat(secciones).allSatisfy(s -> assertThat(s.getVersionCreacion()).isEqualTo(1));
        assertThat(secciones.stream().filter(s -> Boolean.TRUE.equals(s.getOpcional())).map(FormularioSeccion::getClave).toList())
                .containsExactly("obligaciones_negativas", "patrimonios", "oea_datos");
        assertThat(MotorFinanciero.aplica(CalculoSubservicioService.plantilla(secciones))).isTrue();
        assertThat(MotorHallazgos.califica(CalculoSubservicioService.plantilla(secciones))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM codigos_calificacion WHERE codigo LIKE 'FIN-%' OR codigo = 'ALR-FIN-01'", Integer.class)).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM codigos_calificacion WHERE codigo LIKE 'FIN-%' AND critico = TRUE", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT campo_servicio FROM formulario_columnas WHERE clave = 'es_oea'", String.class)).isEqualTo("ES_OEA");
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
        Set<String> sinMarcador = new TreeSet<>(campos);
        sinMarcador.removeAll(deCampos);
        assertThat(sinMarcador).containsExactly("es_oea", "ev_criticidad", "fin_ejecutivo", "fin_ejecutivo_detalle", "fin_meses_mora_30_60",
                "fin_meses_mora_90", "fin_ocultamiento_detalle", "oea_anterior", "oea_inconsistencia", "oea_inconsistencia_detalle", "oea_obs");
        Set<String> columnasSinMarcador = new TreeSet<>(columnasTabla);
        columnasSinMarcador.removeAll(deColumnas);
        assertThat(columnasSinMarcador).containsExactly("ia_detalle", "ia_situacion", "ia_tratamiento", "o_dias", "o_valor", "pt_estado", "pt_explicacion");
        assertThat(deTablas).containsExactlyInAnyOrder("hallazgos", "informativos", "obligaciones_negativas", "oea", "patrimonios");
    }

    // ─────────────────────────── casos de motor_fin.py ───────────────────────────

    @Test
    @DisplayName("F1 Sin novedades (Estándar): 0,0 BAJO, con totales, salvedad y concepto en la sección 8")
    void f1() {
        CalculoFormulario c = calcular(f("Estándar"));
        assertThat(c.resultado().formula()).isEqualTo("Sin hallazgos que sumen puntos");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("0,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.BAJO);
        assertThat(avisos(c)).isEmpty();
        assertThat(c.campos()).containsEntry("s_tot_vig", "2").containsEntry("s_tot_cuota", "580").containsEntry("s_tot_mora", "0")
                .containsEntry("cr_cuotas", "$580 mil").containsEntry("cp_ref", "—").containsEntry("cr_salvedad", SALVEDAD)
                .containsEntry("sec_concepto", "8").containsEntry("fin_ingreso", "$3.000 mil").containsEntry("fin_endeudamiento", "25 %")
                .containsEntry("fin_endeudamiento_ref", "— (no supera el 70 %)").containsEntry("cr_score", "780")
                .containsEntry("cr_banda_1", "150 a 400 Muy bajo").containsEntry("cr_banda_4", "601 a 950 Alto")
                .containsEntry("cr_m1", "").containsEntry("cr_m4", "▲")
                .containsEntry("sem_bajo", "▲ 0,0 puntos").containsEntry("sem_medio", "").containsEntry("sem_alto", "");
        assertThat(c.tablas().get("oea")).isEmpty();
        assertThat(c.tablas().get("informativos")).containsExactly(Map.of("i_situacion", "Indicador de pago (score)",
                "i_detalle", "780 – banda Alto", "i_tratamiento", "Informativo: no suma puntos."));
    }

    @Test
    @DisplayName("F2 Tres meses con mora 30 o 60 (Estándar): FIN-03, 25,0 BAJO")
    void f2() {
        Map<String, String> f = f("Estándar", 0, 3, 25, 780);
        sector(f, "tel", 1, 2, 3, 0, 80, 60);
        CalculoFormulario c = calcular(f);
        assertThat(c.resultado().formula()).isEqualTo("FIN-03: 25 de 100 posibles");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("25,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.BAJO);
        assertThat(avisos(c)).isEmpty();
        assertThat(c.campos()).containsEntry("cp_ref", "FIN-03 (3 meses con mora 30 o 60)");
    }

    @Test
    @DisplayName("F3 Mora 90 o más en 2 meses + moras repetidas (Estándar): 45 + 25 = 70, ALTO")
    void f3() {
        Map<String, String> f = f("Estándar", 2, 3, 25, 780);
        sector(f, "tel", 1, 0, 1, 0, 0, 386);
        CalculoFormulario c = calcular(f);
        assertThat(codigos(c)).containsExactly("FIN-02", "FIN-03");
        assertThat(c.resultado().formula()).isEqualTo("45 + 25 = 70 de 100 posibles");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("70,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(avisos(c)).isEmpty();
        assertThat(c.tablas().get("hallazgos").get(0)).containsEntry("h_fuente", "Consulta de síntesis")
                .containsEntry("h_detalle", "Mora 90 o más en los 2 meses más recientes; saldo en mora $386 mil");
        assertThat(c.campos()).containsEntry("cp_ref", "FIN-02 (2 meses con mora 90 o más) · FIN-03 (3 meses con mora 30 o 60)");
    }

    @Test
    @DisplayName("F4 Mora 90 o más en 6 meses seguidos (Sensible): FIN-01, 60,0 ALTO")
    void f4() {
        Map<String, String> f = f("Sensible", 6, 0, 25, 780);
        sector(f, "real", 1, 0, 1, 0, 0, 2400);
        CalculoFormulario c = calcular(f);
        assertThat(c.resultado().formula()).isEqualTo("FIN-01: 60 de 100 posibles");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("60,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(c.campos()).containsEntry("cp_ref", "FIN-01 (6 meses seguidos con mora 90 o más)");
    }

    @Test
    @DisplayName("F5 Endeudamiento 78 % (Estándar): FIN-04, 40,0 MEDIO")
    void f5() {
        CalculoFormulario c = calcular(f("Estándar", 0, 0, 78, 780));
        assertThat(c.resultado().formula()).isEqualTo("FIN-04: 40 de 100 posibles");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("40,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.MEDIO);
        assertThat(c.campos()).containsEntry("fin_endeudamiento_ref", "FIN-04 (78 %, supera el 70 %)");
    }

    @Test
    @DisplayName("F6 Embargo vigente + ocultó la mora en la visita (Crítico): 45 + 50 + 40 = 135 → 100, ALTO")
    void f6() {
        Map<String, String> f = f("Crítico", 1, 0, 25, 780);
        sector(f, "tel", 1, 0, 1, 0, 0, 150);
        f.put("fin_ejecutivo", "Sí");
        f.put("fin_ejecutivo_detalle", "Embargo de salario vigente (proceso ejecutivo)");
        f.put("c_mor_res", OMITIO);
        CalculoFormulario c = calcular(f);
        assertThat(codigos(c)).containsExactly("FIN-02", "FIN-05", "FIN-08");
        assertThat(c.resultado().formula()).isEqualTo("45 + 50 + 40 = 135 → 100 (tope máximo)");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("100,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(c.tablas().get("hallazgos").get(1)).containsEntry("h_detalle", "Embargo de salario vigente (proceso ejecutivo)");
        assertThat(c.tablas().get("hallazgos").get(2)).containsEntry("h_fuente", "Consolidado financiero").containsEntry("h_detalle", "Omitió u ocultó: moras y reportes");
        assertThat(c.tablas().get("hallazgos").get(0)).containsEntry("h_detalle", "Mora 90 o más en el mes más reciente; saldo en mora $150 mil");
    }

    @Test
    @DisplayName("F7 OEA: castigada + codeudor en mora + patrimonio inconsistente (Estándar): 60 + 20 + 30 = 110 → 100, ALTO")
    void f7() {
        Map<String, String> f = f("Estándar", 6, 0, 25, 780);
        sector(f, "real", 2, 1, 2, 1, 0, 3100);
        f.put("oea_inconsistencia", "Sí");
        f.put("oea_inconsistencia_detalle", "Vehículo nuevo de alto valor que no se explica con el ingreso estimado");
        CalculoFormulario c = calcular(f, true,
                List.of(ob("Almacén de ejemplo", "Castigada", 400, 1900, "Principal", "CAU"),
                        ob("Cooperativa de ejemplo", "En mora", 120, 1200, "Codeudor y otros", "CAC")),
                List.of(pt("Vehículo", "Nuevo", "")));
        assertThat(codigos(c)).containsExactly("FIN-01", "FIN-06", "FIN-07");
        assertThat(c.resultado().formula()).isEqualTo("60 + 20 + 30 = 110 → 100 (tope máximo)");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("100,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(avisos(c)).isEmpty();
        assertThat(c.tablas().get("hallazgos").get(0)).containsEntry("h_fuente", "Reporte detallado")
                .containsEntry("h_detalle", "Almacén de ejemplo (CAU), principal, castigada, 400 días, $1.900 mil");
        assertThat(c.tablas().get("hallazgos").get(1)).containsEntry("h_detalle", "Cooperativa de ejemplo (CAC), codeudor y otros, en mora, 120 días, $1.200 mil");
        assertThat(c.tablas().get("obligaciones_negativas")).extracting(r -> r.get("o_ref") + "|" + r.get("o_dias_mora") + "|" + r.get("o_valor_mora"))
                .containsExactly("FIN-01|400 días|1.900", "FIN-06|120 días|1.200");
        assertThat(c.tablas().get("patrimonios")).extracting(r -> r.get("pt_novedad")).containsExactly("Nuevo");
        assertThat(c.campos()).containsEntry("sec_concepto", "10").containsEntry("cr_salvedad", "").containsEntry("cp_ref", "Moras de 90 días o más: ver sección 8");
        assertThat(c.tablas().get("oea")).hasSize(1);
        assertThat(c.tablas().get("oea").get(0)).containsEntry("oea_negativas_nota", "").containsEntry("oea_estudio_anterior", "Primer estudio");
    }

    @Test
    @DisplayName("F8 Score muy bajo sin códigos (Estándar): 0,0 BAJO y el aviso ALR-FIN-01")
    void f8() {
        CalculoFormulario c = calcular(f("Estándar", 0, 0, 25, 380));
        assertThat(c.resultado().puntajeTexto()).isEqualTo("0,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.BAJO);
        assertThat(avisos(c)).containsExactly("ALR-FIN-01: score en la banda más baja sin ningún código FIN marcado. Revise el reporte antes de cerrar.");
        assertThat(c.campos()).containsEntry("cr_m1", "▲").containsEntry("cr_m4", "");
        assertThat(c.tablas().get("informativos").get(0)).containsEntry("i_detalle", "380 – banda Muy bajo");
    }

    @Test
    @DisplayName("F9 OEA sin reportes negativos, primer estudio (Sensible): 0,0 BAJO y la nota de la sección 8")
    void f9() {
        CalculoFormulario c = calcular(f("Sensible"), true, List.of(), List.of(pt("Apartamento", "Primer estudio", "")));
        assertThat(c.resultado().formula()).isEqualTo("Sin hallazgos que sumen puntos");
        assertThat(c.resultado().puntajeTexto()).isEqualTo("0,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.BAJO);
        assertThat(avisos(c)).isEmpty();
        assertThat(c.tablas().get("oea").get(0)).containsEntry("oea_negativas_nota", "No registra obligaciones con reporte negativo en el reporte detallado.")
                .containsEntry("oea_estudio_anterior", "Primer estudio");
    }

    // ─────────────────────────── reglas propias ───────────────────────────

    @Test
    @DisplayName("En OEA la barra no genera FIN-01/02: manda el detalle; saldada castigada y menos de 90 días no suman")
    void oeaManda() {
        Map<String, String> f = f("Estándar", 3, 0, 25, 780);
        sector(f, "fin", 1, 1, 2, 0, 500, 900);
        CalculoFormulario c = calcular(f, true,
                List.of(ob("Banco de ejemplo", "Saldada castigada", 500, 300, "Principal", "CAB"),
                        ob("Tienda de ejemplo", "En mora", 45, 100, "Principal", "TDC"),
                        ob("Telefonía de ejemplo", "Cartera de difícil cobro", 100, 250, "Principal", "CTC")),
                List.of(pt("Casa", "Sin cambio", "")));
        assertThat(codigos(c)).containsExactly("FIN-02");
        assertThat(c.tablas().get("obligaciones_negativas")).extracting(r -> r.get("o_ref")).containsExactly("—", "—", "FIN-02");
        assertThat(c.tablas().get("oea").get(0)).containsEntry("oea_negativas_nota", "");
    }

    @Test
    @DisplayName("FIN-01 por más de 180 días aunque no figure castigada; FIN-06 pesa más que los días")
    void diasYCodeudor() {
        CalculoFormulario c = calcular(f("Estándar"), true,
                List.of(ob("A", "En mora", 181, 10, "Principal", "CAB"), ob("B", "Castigada", 900, 10, "Codeudor y otros", "CAB"),
                        ob("C", "En mora", 180, 10, "Principal", "CAB")),
                List.of());
        assertThat(codigos(c)).containsExactly("FIN-01", "FIN-06", "FIN-02");
        assertThat(c.resultado().formula()).isEqualTo("60 + 20 + 45 = 125 → 100 (tope máximo)");
    }

    @Test
    @DisplayName("Ingreso 'No reportado': FIN-04 no aplica y se imprime 'No reportado'")
    void sinIngreso() {
        Map<String, String> f = f("Estándar", 0, 0, 90, 780);
        f.put("fin_ingreso", "No reportado");
        CalculoFormulario c = calcular(f);
        assertThat(c.resultado().puntajeTexto()).isEqualTo("0,0");
        assertThat(c.campos()).containsEntry("fin_ingreso", "No reportado").containsEntry("fin_endeudamiento", "No reportado")
                .containsEntry("fin_endeudamiento_ref", "— (sin ingreso estimado, FIN-04 no aplica)");
    }

    @Test
    @DisplayName("Sin score: 'No reportado', ninguna banda marcada y ninguna fila de score")
    void sinScore() {
        Map<String, String> f = f("Estándar");
        f.remove("cr_score");
        CalculoFormulario c = calcular(f);
        assertThat(c.campos()).containsEntry("cr_score", "No reportado").containsEntry("cr_m1", "").containsEntry("cr_m4", "");
        assertThat(c.tablas().get("informativos")).isEmpty();
    }

    @Test
    @DisplayName("Los hallazgos sin impacto del analista salen después de la fila del score")
    void informativosDelAnalista() {
        Map<String, List<Map<String, String>>> tablas = new HashMap<>();
        tablas.put("informativos_analista", List.of(Map.of("ia_situacion", "Moras antiguas ya pagadas", "ia_detalle", "2019", "ia_tratamiento", "Sin puntos")));
        CalculoFormulario c = calculo.calcular(secciones, null, Map.of("es_oea", "No"), f("Estándar"), tablas).orElseThrow();
        assertThat(c.tablas().get("informativos")).extracting(r -> r.get("i_situacion")).containsExactly("Indicador de pago (score)", "Moras antiguas ya pagadas");
    }

    @Test
    @DisplayName("Avisos de validar(): mora 90 sin saldo, saldo sin meses, OEA sin obligaciones ni bienes, bien nuevo sin explicación, falta el %")
    void avisosDeValidar() {
        Map<String, String> f = f("Estándar", 2, 0, 25, 780);
        f.remove("fin_endeudamiento");
        CalculoFormulario c = calcular(f);
        assertThat(avisos(c)).contains("Hay meses con mora 90 o más pero el saldo en mora es 0: revise la consulta de síntesis.",
                "Falta el % de utilización del ingreso estimado (o marque el ingreso como \"No reportado\").");

        Map<String, String> g = f("Estándar");
        sector(g, "tel", 1, 0, 1, 0, 0, 200);
        assertThat(avisos(calcular(g))).contains("Hay saldo en mora pero ningún mes en mora en la barra: revise la consulta de síntesis.");
        assertThat(avisos(calcular(g, true, List.of(), List.of()))).contains(
                "Estudio OEA con saldo en mora y sin obligaciones registradas en la sección 8: registre el detalle del reporte detallado.",
                "Estudio OEA sin bienes registrados: si no tiene bienes, registre una fila \"No registra\".");
        assertThat(avisos(calcular(f("Estándar"), true, List.of(), List.of(pt("Casa", "Ya no aparece", ""))))).contains(
                "Bien \"Casa\": marcado \"Ya no aparece\" sin explicación; revise si corresponde FIN-07.");
        Map<String, String> h = f("Estándar", 1, 0, 25, 780);
        sector(h, "tel", 1, 0, 1, 0, 0, 100);
        assertThat(avisos(calcular(h, true, List.of(ob("X", "En mora", 30, 10, "Principal", "CAB")), List.of(pt("Casa", "Sin cambio", ""))))).contains(
                "La barra muestra mora 90 o más pero ninguna obligación del reporte detallado tiene 90 días o más: revise.");
    }

    @Test
    @DisplayName("Sin criticidad no se califica y se avisa")
    void sinCriticidad() {
        Map<String, String> f = f("Estándar");
        f.remove("ev_criticidad");
        CalculoFormulario c = calcular(f);
        assertThat(c.aviso()).isEqualTo(MotorHallazgos.FALTA_CRITICIDAD);
        assertThat(c.resultado()).isNull();
        assertThat(c.campos()).containsEntry("srv_puntaje", "").containsEntry("s_tot_cuota", "580");
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
