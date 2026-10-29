package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.formularios.entity.FormularioColumna;
import com.polygraph.erp.modules.formularios.entity.FormularioSeccion;
import com.polygraph.erp.modules.formularios.repository.FormularioSeccionRepository;
import com.polygraph.erp.shared.enums.CampoCandidato;
import com.polygraph.erp.shared.enums.TipoDatoFormulario;
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/** Prompt 2 de docs/antecedentes: las claves del formulario de Antecedentes (lo que arma V204) son
 *  letra por letra los marcadores de la plantilla Word. Se recorre word/document.xml (y encabezado y
 *  pie) buscando ${campos['x']}, ${candidato['X']} y ${['x']}, y word/comments.xml buscando
 *  repeatTableRow/repeatDocPart(tablas['x']). Solo ev_criticidad y a_ref existen en el formulario sin
 *  marcador. En pruebas no corre Flyway (H2): se ejecuta el SQL real de V204 sobre las tablas que
 *  crea Hibernate. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("Formulario de Antecedentes (V207) = marcadores del Word")
class PlantillaAntecedentesWordTest {

    private static final java.nio.file.Path WORD = PlantillaAntecedentesPrueba.WORD;

    private static final Set<String> SIN_MARCADOR = Set.of("ev_criticidad", "a_ref");
    private static final Set<String> FICHA = Set.of("NOMBRE_COMPLETO", "TIPO_DOCUMENTO", "CEDULA", "FECHA_EXPEDICION",
            "FECHA_NACIMIENTO", "LUGAR_NACIMIENTO", "RUT");
    private static final Set<String> CALCULADOS = Set.of("srv_formula", "srv_puntaje", "sem_bajo", "sem_medio", "sem_alto",
            "transito_nota", "alertas_id_nota", "procesos_nota", "procesos_priv_nota", "p_numero", "pp_numero",
            "h_ref", "h_descripcion", "h_fuente", "h_detalle", "h_puntos");
    private static final Set<String> REFERENCIAS = Set.of("ln_ref", "li_ref", "simit_ref", "runt_ref", "a_ref", "p_calificacion", "pp_calificacion");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private FormularioSeccionRepository seccionRepository;

    private List<FormularioSeccion> secciones;
    private List<FormularioColumna> columnas;

    @BeforeEach
    void aplicarMigracion() throws Exception {
        TipoProgreso antecedentes = PlantillaAntecedentesPrueba.aplicarV207(jdbc, em);

        secciones = seccionRepository.findByTipoProgreso_IdTipoProgresoAndActivoTrueOrderByOrdenAsc(antecedentes.getIdTipoProgreso());
        columnas = secciones.stream().flatMap(s -> s.getColumnas().stream()).filter(c -> Boolean.TRUE.equals(c.getActivo())).toList();
    }

    @Test
    @DisplayName("Los campos, las tablas y sus columnas coinciden con los marcadores del Word")
    void clavesIgualesAlWord() throws Exception {
        Marcadores word = leerWord();

        Set<String> campos = claves(TipoSeccionFormulario.CAMPOS);
        Set<String> tablas = secciones.stream().filter(s -> s.getTipo() == TipoSeccionFormulario.TABLA)
                .map(FormularioSeccion::getClave).collect(Collectors.toSet());
        Set<String> columnasTabla = claves(TipoSeccionFormulario.TABLA);

        assertThat(word.campos).as("${campos[...]} del Word").isEqualTo(sin(campos, SIN_MARCADOR));
        // Anexo 1 (catálogo y alertas) son tablas del sistema, no secciones del formulario.
        assertThat(word.tablas).as("tablas[...] de los comentarios del Word")
                .isEqualTo(union(tablas, FormularioPlantillaWordService.TABLAS_CATALOGO));
        assertThat(word.filas).as("${[...]} dentro de las tablas del Word")
                .isEqualTo(union(sin(columnasTabla, SIN_MARCADOR), Set.of("a1_ref", "a1_hallazgo", "a1_calificacion")));

        Set<String> todas = new HashSet<>(campos);
        todas.addAll(columnasTabla);
        Set<String> enWord = new HashSet<>(word.campos);
        enWord.addAll(word.filas);
        assertThat(sin(todas, enWord)).as("claves del formulario sin marcador").isEqualTo(SIN_MARCADOR);
    }

    @Test
    @DisplayName("Los datos de la ficha vienen del candidato (no se piden al analista)")
    void fichaDelCandidato() throws Exception {
        Set<String> candidato = leerWord().candidato;
        assertThat(candidato).isEqualTo(FICHA);
        Set<String> disponibles = Arrays.stream(CampoCandidato.values()).map(Enum::name).collect(Collectors.toSet());
        assertThat(disponibles).containsAll(candidato);
        assertThat(columnas).extracting(FormularioColumna::getClave).doesNotContainAnyElementsOf(FICHA);
    }

    @Test
    @DisplayName("ev_criticidad es una lista obligatoria Estándar | Sensible | Crítico")
    void criticidad() {
        FormularioColumna c = columna("ev_criticidad");
        assertThat(c.getTipoDato()).isEqualTo(TipoDatoFormulario.LISTA);
        assertThat(OpcionesLista.leer(c.getOpciones())).containsExactly("Estándar", "Sensible", "Crítico");
        assertThat(c.getObligatorio()).isTrue();
    }

    @Test
    @DisplayName("Las columnas Ref./Calificación arrancan en \"—\" y no califican con el constructor")
    void referencias() {
        for (String clave : REFERENCIAS) {
            FormularioColumna c = columna(clave);
            assertThat(c.getTipoDato()).as(clave).isEqualTo(TipoDatoFormulario.LISTA);
            assertThat(OpcionesLista.leer(c.getOpciones())).as(clave).startsWith("—");
            assertThat(c.getValorDefecto()).as(clave).isEqualTo("—");
        }
        assertThat(OpcionesLista.leer(columna("p_calificacion").getOpciones()))
                .containsExactly("—", "LEG-05", "LEG-11", "LEG-19", "LEG-20", "LEG-21", "LEG-22", "LEG-23");
        assertThat(columnas).allMatch(c -> !Boolean.TRUE.equals(c.getCalificaRiesgo()));
        for (String clave : REFERENCIAS) assertThat(columna(clave).getUsoCalificacion()).as(clave).isEqualTo("HALLAZGO");
        assertThat(columna("ev_criticidad").getUsoCalificacion()).isEqualTo("CRITICIDAD");
        assertThat(columna("ev_conduccion").getUsoCalificacion()).isEqualTo("CONDICION");
        assertThat(columna("ev_estado_doc").getHallazgos()).extracting(h -> h.getOpcion() + "=" + h.getCodigo())
                .containsExactlyInAnyOrder("Cancelada por muerte=LEG-01", "Cancelada por doble cedulación=LEG-01", "No encontrada=LEG-01");
        assertThat(columna("dec2_coincide").getHallazgos()).extracting(h -> h.getOpcion() + "=" + h.getCodigo()).containsExactly("No=LEG-12");
    }

    @Test
    @DisplayName("Las opciones con coma se conservan enteras (17 tipos de cargo del Anexo 2)")
    void opcionesConComa() {
        List<String> cargos = OpcionesLista.leer(columna("ev_tipo_cargo").getOpciones());
        assertThat(cargos).hasSize(17).contains("Manejo de dinero, valores o inventarios").endsWith("Otro");
    }

    @Test
    @DisplayName("Lo calculado no lo edita el analista y la tabla hallazgos la arma el código")
    void calculados() {
        assertThat(columnas.stream().filter(c -> Boolean.TRUE.equals(c.getCalculado())).map(FormularioColumna::getClave)
                .collect(Collectors.toSet())).isEqualTo(CALCULADOS);
        assertThat(secciones.stream().filter(s -> Boolean.TRUE.equals(s.getCalculada())).map(FormularioSeccion::getClave))
                .containsExactly("hallazgos");
        assertThat(columnas).filteredOn(c -> CALCULADOS.contains(c.getClave())).allMatch(FormularioColumna::esAutomatica);
    }

    @Test
    @DisplayName("Los datos del estudio salen de la plataforma (el diccionario marca el profesional como Plataforma)")
    void datosDelEstudio() {
        assertThat(columna("estudio_numero").getCampoServicio()).isEqualTo("NUMERO_ESTUDIO");
        assertThat(columna("fecha_emision").getCampoServicio()).isEqualTo("FECHA_EMISION");
        assertThat(columna("ev_cliente").getCampoServicio()).isEqualTo("CLIENTE");
        assertThat(columna("ev_cargo").getCampoServicio()).isEqualTo("CARGO");
        assertThat(columna("analista_nombre").getCampoServicio()).isEqualTo("PROFESIONAL");
    }

    // ───────────────────────────── utilidades ─────────────────────────────

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> r = new HashSet<>(a);
        r.addAll(b);
        return r;
    }

    private record Marcadores(Set<String> campos, Set<String> candidato, Set<String> filas, Set<String> tablas) {}

    private static final Pattern CAMPO = Pattern.compile("\\$\\{campos\\['([^']+)'\\]\\}");
    private static final Pattern CANDIDATO = Pattern.compile("\\$\\{candidato\\['([^']+)'\\]\\}");
    private static final Pattern FILA = Pattern.compile("\\$\\{\\['([^']+)'\\]\\}");
    private static final Pattern TABLA = Pattern.compile("repeat(?:TableRow|DocPart)\\(tablas\\['([^']+)'\\]\\)");

    private static Marcadores leerWord() throws Exception {
        StringBuilder cuerpo = new StringBuilder();
        String comentarios = "";
        try (ZipFile zip = new ZipFile(WORD.toFile())) {
            for (ZipEntry e : zip.stream().toList()) {
                String nombre = e.getName();
                if (!nombre.startsWith("word/") || !nombre.endsWith(".xml")) continue;
                String texto;
                try (InputStream in = zip.getInputStream(e)) {
                    texto = new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("<[^>]+>", "");
                }
                if (nombre.equals("word/comments.xml")) comentarios = texto;
                else if (nombre.equals("word/document.xml") || nombre.startsWith("word/header") || nombre.startsWith("word/footer")) cuerpo.append(texto);
            }
        }
        return new Marcadores(todos(CAMPO, cuerpo), todos(CANDIDATO, cuerpo), todos(FILA, cuerpo), todos(TABLA, comentarios));
    }

    private static Set<String> todos(Pattern p, CharSequence texto) {
        Set<String> r = new HashSet<>();
        Matcher m = p.matcher(texto);
        while (m.find()) r.add(m.group(1));
        return r;
    }

    private Set<String> claves(TipoSeccionFormulario tipo) {
        return secciones.stream().filter(s -> s.getTipo() == tipo)
                .flatMap(s -> s.getColumnas().stream()).filter(c -> Boolean.TRUE.equals(c.getActivo()))
                .map(FormularioColumna::getClave).collect(Collectors.toSet());
    }

    private FormularioColumna columna(String clave) {
        return columnas.stream().filter(c -> clave.equals(c.getClave())).findFirst()
                .orElseThrow(() -> new AssertionError("No existe la columna " + clave));
    }

    private static Set<String> sin(Set<String> base, Set<String> quitar) {
        Set<String> r = new HashSet<>(base);
        r.removeAll(quitar);
        return r;
    }
}
