package com.polygraph.erp.modules.riesgo.repository;

import com.polygraph.erp.modules.riesgo.entity.CodigoCalificacion;
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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** El catálogo de Antecedentes que siembra V202 es exactamente el de docs/antecedentes/motor_ant.py
 *  (CATALOGO) y la hoja "Catálogo" del diccionario. En pruebas Flyway está apagado (H2), así que se
 *  ejecuta el INSERT real de la migración sobre la tabla que crea Hibernate y se lee con el
 *  repositorio — la misma consulta que usará el cálculo. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("Catálogo de códigos de Antecedentes legales (V202)")
class CodigoCalificacionCatalogoTest {

    private static final Path MIGRACION = Path.of("src/main/resources/db/migration/V202__codigos_calificacion.sql");
    private static final Path MIGRACION_UMBRALES = Path.of("src/main/resources/db/migration/V190__calificacion_riesgo_configuracion.sql");

    /** codigo → {puntos o "Crítico", por_proceso, solo_conduccion}, copiado de motor_ant.py. */
    private static final Map<String, Object[]> ESPERADO = Map.ofEntries(
            Map.entry("LEG-01", new Object[]{"Crítico", false, false}),
            Map.entry("LEG-02", new Object[]{"Crítico", false, false}),
            Map.entry("LEG-03", new Object[]{"Crítico", false, false}),
            Map.entry("LEG-04", new Object[]{"Crítico", false, false}),
            Map.entry("LEG-05", new Object[]{"Crítico", true, false}),
            Map.entry("LEG-06", new Object[]{50, false, false}),
            Map.entry("LEG-07", new Object[]{50, false, false}),
            Map.entry("LEG-08", new Object[]{45, false, false}),
            Map.entry("LEG-09", new Object[]{"Crítico", false, false}),
            Map.entry("LEG-10", new Object[]{"Crítico", false, true}),
            Map.entry("LEG-11", new Object[]{70, true, false}),
            Map.entry("LEG-12", new Object[]{25, false, false}),
            Map.entry("LEG-13", new Object[]{40, false, false}),
            Map.entry("LEG-14", new Object[]{40, false, true}),
            Map.entry("LEG-15", new Object[]{20, false, false}),
            Map.entry("LEG-16", new Object[]{15, false, false}),
            Map.entry("LEG-17", new Object[]{20, false, true}),
            Map.entry("LEG-18", new Object[]{30, false, false}),
            Map.entry("LEG-19", new Object[]{15, true, false}),
            Map.entry("LEG-20", new Object[]{30, true, false}),
            Map.entry("LEG-21", new Object[]{20, true, false}),
            Map.entry("LEG-22", new Object[]{10, true, false}),
            Map.entry("LEG-23", new Object[]{5, true, false}),
            Map.entry("LEG-24", new Object[]{"Crítico", false, false}));

    @Autowired private CodigoCalificacionRepository repository;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cargarCatalogoDeLaMigracion() throws Exception {
        jdbc.execute(sentencia(MIGRACION, "INSERT INTO codigos_calificacion"));
    }

    @Test
    @DisplayName("Tiene los 24 códigos LEG, en orden y todos activos")
    void tiene24Codigos() {
        List<CodigoCalificacion> catalogo = repository.findByGrupoAndActivoTrueOrderByOrdenAsc("ANTECEDENTES");

        assertThat(catalogo).hasSize(24);
        assertThat(catalogo).extracting(CodigoCalificacion::getCodigo)
                .containsExactlyElementsOf(ESPERADO.keySet().stream().sorted().toList());
    }

    @Test
    @DisplayName("Puntos, crítico, por proceso y solo conducción coinciden con motor_ant.py")
    void puntosCoinciden() {
        Map<String, CodigoCalificacion> porCodigo = repository.findByGrupoAndActivoTrueOrderByOrdenAsc("ANTECEDENTES")
                .stream().collect(Collectors.toMap(CodigoCalificacion::getCodigo, c -> c));

        ESPERADO.forEach((codigo, e) -> {
            CodigoCalificacion c = porCodigo.get(codigo);
            assertThat(c).as(codigo).isNotNull();
            if ("Crítico".equals(e[0])) {
                assertThat(c.getCritico()).as(codigo + " crítico").isTrue();
                assertThat(c.getPuntos()).as(codigo + " puntos").isNull();
            } else {
                assertThat(c.getCritico()).as(codigo + " crítico").isFalse();
                assertThat(c.getPuntos()).as(codigo + " puntos").isEqualTo(e[0]);
            }
            assertThat(c.getPorProceso()).as(codigo + " por proceso").isEqualTo(e[1]);
            assertThat(c.getSoloConduccion()).as(codigo + " solo conducción").isEqualTo(e[2]);
        });
    }

    @Test
    @DisplayName("Las alertas ALR no están en el catálogo de cálculo")
    void sinAlertas() {
        assertThat(repository.findAll()).extracting(CodigoCalificacion::getCodigo)
                .noneMatch(c -> c.startsWith("ALR"));
    }

    @Test
    @DisplayName("Los umbrales por criticidad ya existen (V190) con los valores del diccionario: se reutilizan")
    void umbralesExistentes() throws Exception {
        String insert = sentencia(MIGRACION_UMBRALES, "INSERT INTO umbrales_riesgo");
        assertThat(insert.replaceAll("\\s+", " "))
                .contains("('ESTANDAR', 30, 60, 31)")
                .contains("('SENSIBLE', 25, 50, 26)")
                .contains("('CRITICO', 20, 40, 21)");
    }

    /** La sentencia completa (hasta el ';' que la cierra) que empieza con el prefijo dado. */
    private static String sentencia(Path archivo, String prefijo) throws Exception {
        String sql = Files.readString(archivo, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile(Pattern.quote(prefijo) + ".*?\\);\\s*$", Pattern.DOTALL | Pattern.MULTILINE).matcher(sql);
        assertThat(m.find()).as("sentencia " + prefijo + " en " + archivo).isTrue();
        String s = m.group().trim();
        return s.substring(0, s.length() - 1);
    }
}
