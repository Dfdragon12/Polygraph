package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.Umbrales;
import com.polygraph.erp.shared.enums.CriticidadCargo;
import com.polygraph.erp.shared.enums.Rol;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Arma en la base de pruebas (H2, sin Flyway) lo REAL de Antecedentes: el subservicio, el
 *  catálogo de hallazgos de V202 y la plantilla vigente — V207 (desde cero, con campos de hallazgo)
 *  y encima V208 (organizada como el ejemplo PDF), ejecutando el SQL de las migraciones tal cual. */
public final class PlantillaAntecedentesPrueba {

    public static final Path WORD = Path.of("../docs/antecedentes/VERIFICACION_ANTECEDENTES_LEGALES_FINAL.docx");
    private static final Path MIGRACIONES = Path.of("src/main/resources/db/migration");

    /** Umbrales de V190. */
    public static final Map<CriticidadCargo, Umbrales> UMBRALES = Map.of(
            CriticidadCargo.ESTANDAR, new Umbrales(new BigDecimal("30"), new BigDecimal("60"), new BigDecimal("31")),
            CriticidadCargo.SENSIBLE, new Umbrales(new BigDecimal("25"), new BigDecimal("50"), new BigDecimal("26")),
            CriticidadCargo.CRITICO, new Umbrales(new BigDecimal("20"), new BigDecimal("40"), new BigDecimal("21")));

    private PlantillaAntecedentesPrueba() {}

    /** Subservicio + catálogo (V202) + plantilla vigente (V207 y V208). */
    public static TipoProgreso aplicarV207(JdbcTemplate jdbc, EntityManager em) throws Exception {
        TipoProgreso antecedentes = subservicio(em);
        jdbc.execute(insertDeV202());
        ejecutar(jdbc, "V207__plantilla_antecedentes_desde_cero.sql");
        ejecutar(jdbc, "V208__plantilla_antecedentes_organizada.sql");
        em.clear();
        return antecedentes;
    }

    static TipoProgreso subservicio(EntityManager em) {
        TipoProgreso antecedentes = TipoProgreso.builder().nombreProgreso("Antecedentes").activo(true)
                .rolResponsable(Rol.ANALISTA_INTERNO).requiereDocumento(true).versionPlantilla(1)
                .grupoCalificacion("ANTECEDENTES").build();
        em.persist(antecedentes);
        em.flush();
        return antecedentes;
    }

    /** La plantilla de antes (V204), para probar que V207 la reemplaza entera. */
    static void aplicarV204(JdbcTemplate jdbc) throws Exception {
        ejecutar(jdbc, "V204__plantilla_antecedentes_legales.sql");
    }

    /** V207 y V208, sobre lo que haya (en producción corren después de V204). */
    static void aplicarSoloV207(JdbcTemplate jdbc) throws Exception {
        ejecutar(jdbc, "V207__plantilla_antecedentes_desde_cero.sql");
        ejecutar(jdbc, "V208__plantilla_antecedentes_organizada.sql");
    }

    static String insertDeV202() throws Exception {
        String sql = Files.readString(MIGRACIONES.resolve("V202__codigos_calificacion.sql"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("INSERT INTO codigos_calificacion.*?\\);\\s*$", Pattern.DOTALL | Pattern.MULTILINE).matcher(sql);
        if (!m.find()) throw new IllegalStateException("V202 sin INSERT");
        String s = m.group().trim();
        return s.substring(0, s.length() - 1);
    }

    private static void ejecutar(JdbcTemplate jdbc, String archivo) throws Exception {
        String sql = Files.readAllLines(MIGRACIONES.resolve(archivo), StandardCharsets.UTF_8).stream()
                .filter(l -> !l.trim().startsWith("--")).collect(Collectors.joining("\n"));
        for (String sentencia : sql.split(";\\s*\\n")) {
            if (!sentencia.isBlank()) jdbc.execute(sentencia.replaceAll(";\\s*$", ""));
        }
    }
}
