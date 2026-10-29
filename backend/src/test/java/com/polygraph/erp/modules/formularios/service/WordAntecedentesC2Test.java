package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService;
import com.polygraph.erp.modules.formularios.entity.FormularioColumna;
import com.polygraph.erp.modules.formularios.entity.FormularioFila;
import com.polygraph.erp.modules.formularios.entity.FormularioPlantillaConfig;
import com.polygraph.erp.modules.formularios.entity.FormularioSeccion;
import com.polygraph.erp.modules.formularios.repository.FormularioSeccionRepository;
import com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CalculoFormulario;
import jakarta.persistence.EntityManager;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Prompt 4 de docs/antecedentes: el Word del caso C2 generado con la plantilla real
 *  (VERIFICACION_ANTECEDENTES_LEGALES_FINAL.docx), el formulario real (V207), el catálogo real
 *  (V202) y el mismo office-stamper de producción. Datos inventados. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("Word de Antecedentes — caso C2")
class WordAntecedentesC2Test {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private FormularioSeccionRepository seccionRepository;
    @Autowired private com.polygraph.erp.modules.riesgo.repository.CodigoCalificacionRepository codigoRepository;

    private List<FormularioSeccion> secciones;
    private final Map<Integer, String> valoresCampos = new HashMap<>();
    private final Map<Integer, List<FormularioFila>> filasPorSeccion = new HashMap<>();
    private final Map<Integer, Map<Integer, String>> valoresPorFila = new HashMap<>();
    private int siguienteFila = 1;

    @BeforeEach
    void prepararCasoC2() throws Exception {
        TipoProgreso antecedentes = PlantillaAntecedentesPrueba.aplicarV207(jdbc, em);
        secciones = seccionRepository.findByTipoProgreso_IdTipoProgresoAndActivoTrueOrderByOrdenAsc(antecedentes.getIdTipoProgreso());

        // Entradas del caso C2 de casos_prueba.py.
        campo("ev_criticidad", "Estándar");
        campo("ev_conduccion", "No");
        campo("ev_estado_doc", "Vigente");
        campo("dec1_coincide", "Sí");
        campo("dec2_coincide", "Sí");
        campo("dec3_coincide", "Sí");
        campo("simit_ref", "LEG-17");
        campo("simit_multas", "2 pendientes");
        fila("listas_nacionales", Map.of("ln_fuente", "Medidas correctivas (RNMC)", "ln_ref", "LEG-18", "ln_observacion", "Multa tipo 2 (2021)"));
        fila("listas_nacionales", Map.of("ln_fuente", "Medidas correctivas (RNMC)", "ln_ref", "LEG-18", "ln_observacion", "Multa tipo 1 (2019), en proceso"));
        fila("procesos_privados", Map.of("pp_delito", "Hurto", "pp_estado", "Inactivo", "pp_calificacion", "LEG-19"));
        fila("procesos_privados", Map.of("pp_delito", "Violencia intrafamiliar", "pp_estado", "Inactivo", "pp_calificacion", "LEG-19"));
    }

    @Test
    @DisplayName("Sin ${ sin resolver, 3 hallazgos (30, 15, 15) y el semáforo en MEDIO con ▲ 60,0 puntos")
    void wordDelCasoC2() throws Exception {
        byte[] word = generar();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(word));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String texto = extractor.getText();

            assertThat(texto).doesNotContain("${");

            // Sección 3: encabezado + 3 filas de hallazgos + la fila fija "Puntaje del servicio" con
            // la que cierra la tabla en la plantilla.
            XWPFTable hallazgos = tablaConEncabezado(doc, "Ref.", "Hallazgo", "Fuente", "Detalle", "Puntos");
            assertThat(hallazgos.getRows()).hasSize(1 + 3 + 1);
            assertThat(celdas(hallazgos.getRow(4))).containsExactly("Puntaje del servicio: 30 + 15 + 15 = 60 de 100 posibles", "60,0");
            assertThat(celdas(hallazgos.getRow(1))).containsExactly("LEG-18", "Medida correctiva registrada (RNMC), en cualquier estado",
                    "Medidas correctivas (RNMC)", "Multa tipo 2 (2021)", "30");
            assertThat(celdas(hallazgos.getRow(2))).startsWith("LEG-19").endsWith("15").contains("Proceso N° 1: Hurto (Inactivo)");
            assertThat(celdas(hallazgos.getRow(3))).startsWith("LEG-19").endsWith("15").contains("Proceso N° 2: Violencia intrafamiliar (Inactivo)");

            // Anexo 1: sale del catálogo de hallazgos de la plataforma (24 LEG de V202), no del Word.
            XWPFTable anexo = tablaConEncabezado(doc, "Ref.", "Hallazgo", "Calificación");
            assertThat(anexo.getRows()).hasSize(1 + 24 + 1); // encabezado, 24 LEG, título de alertas (sin alertas en esta base)
            assertThat(celdas(anexo.getRow(1))).containsExactly("LEG-01", "Documento cancelado o no encontrado en la Registraduría", "Crítico");
            assertThat(celdas(anexo.getRow(18))).containsExactly("LEG-18", "Medida correctiva registrada (RNMC), en cualquier estado", "30");

            // Fórmula, puntaje y semáforo: solo MEDIO lleva texto.
            assertThat(texto).contains("30 + 15 + 15 = 60 de 100 posibles").contains("60,0");
            assertThat(texto.split("▲", -1)).hasSize(2);
            // Semáforo: título, fila BAJO | MEDIO | ALTO y debajo la marca del nivel obtenido.
            XWPFTable semaforo = tablaConEncabezado(doc, "SEMÁFORO DE RIESGO DEL SERVICIO");
            assertThat(celdas(semaforo.getRow(1))).containsExactly("BAJO", "MEDIO", "ALTO");
            assertThat(celdas(semaforo.getRow(2))).containsExactly("", "▲ 60,0 puntos", "");

            // Notas: tránsito informativo (cargo sin conducción), sin alertas, sin procesos en Rama
            // Judicial; en bases privadas sí hay, así que esa nota no va.
            assertThat(texto).contains("Informativo: el cargo no implica conducción; los resultados de tránsito no suman puntos.")
                    .contains("Sin alertas reportadas.")
                    .contains("No se encontraron procesos judiciales en Rama Judicial.")
                    .doesNotContain("No se encontraron procesos en bases privadas.");

            // Sección 12: un bloque por proceso, con su número consecutivo.
            assertThat(texto).contains("PROCESO N° 1").contains("PROCESO N° 2").doesNotContain("PROCESO N° 3");
        }
    }

    @Test
    @DisplayName("Sin ev_criticidad: el Word igual se genera, sin puntaje ni hallazgos, y la pantalla recibe el aviso")
    void sinCriticidad() throws Exception {
        campo("ev_criticidad", "");
        CalculoFormulario calculo = calcular();
        assertThat(calculo.aviso()).isEqualTo("Falta la criticidad del cargo.");
        assertThat(calculo.campos()).containsEntry("srv_puntaje", "").containsEntry("sem_medio", "");

        byte[] word = generar();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(word));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            assertThat(extractor.getText()).doesNotContain("${").doesNotContain("▲");
            // Solo encabezado y la fila fija de puntaje, vacía: ningún hallazgo.
            XWPFTable hallazgos = tablaConEncabezado(doc, "Ref.", "Hallazgo", "Fuente", "Detalle", "Puntos");
            assertThat(hallazgos.getRows()).hasSize(2);
            assertThat(celdas(hallazgos.getRow(1))).containsExactly("Puntaje del servicio:", "");
        }
    }

    // ───────────────────────────── utilidades ─────────────────────────────

    private CalculoFormulario calcular() {
        ValoresPorClave.Entrada entrada = ValoresPorClave.armar(secciones, valoresCampos, filasPorSeccion, valoresPorFila);
        var catalogo = CalculoSubservicioService.catalogoDe(codigoRepository.findAll());
        return new CalculoSubservicioService(() -> catalogo, () -> PlantillaAntecedentesPrueba.UMBRALES)
                .calcular(secciones, null, entrada.campos(), entrada.tablas()).orElseThrow();
    }

    private byte[] generar() {
        CandidatoInfoService candidatoInfo = mock(CandidatoInfoService.class);
        when(candidatoInfo.resolverUno(any(), anyString())).thenReturn("(dato del candidato)");
        when(candidatoInfo.resolverUnoServicio(any(), anyString())).thenReturn("(dato del estudio)");
        when(candidatoInfo.resolverTablasFijas(any())).thenReturn(Map.of());
        AlmacenamientoDocumentosService almacenamiento = mock(AlmacenamientoDocumentosService.class);
        when(almacenamiento.cargarComoResource("antecedentes.docx")).thenReturn(new FileSystemResource(PlantillaAntecedentesPrueba.WORD));

        FormularioPlantillaWordService word = new FormularioPlantillaWordService(candidatoInfo, almacenamiento, codigoRepository);
        return word.generar(FormularioPlantillaConfig.builder().plantillaWordRuta("antecedentes.docx").build(), null, null, null,
                secciones, valoresCampos, filasPorSeccion, valoresPorFila, true, null, null, calcular());
    }

    private void campo(String clave, String valor) {
        valoresCampos.put(columna(clave).getIdColumna(), valor);
    }

    private void fila(String tabla, Map<String, String> valores) {
        FormularioSeccion seccion = secciones.stream().filter(s -> tabla.equals(s.getClave())).findFirst().orElseThrow();
        List<FormularioFila> filas = filasPorSeccion.computeIfAbsent(seccion.getIdSeccion(), k -> new ArrayList<>());
        FormularioFila f = FormularioFila.builder().idFila(siguienteFila++).seccion(seccion).etiqueta("").orden(filas.size()).build();
        filas.add(f);
        Map<Integer, String> porColumna = new HashMap<>();
        valores.forEach((clave, valor) -> porColumna.put(columna(clave).getIdColumna(), valor));
        valoresPorFila.put(f.getIdFila(), porColumna);
    }

    private FormularioColumna columna(String clave) {
        return secciones.stream().flatMap(s -> s.getColumnas().stream())
                .filter(c -> Boolean.TRUE.equals(c.getActivo()) && clave.equals(c.getClave()))
                .findFirst().orElseThrow(() -> new AssertionError("No existe la columna " + clave));
    }

    private static XWPFTable tablaConEncabezado(XWPFDocument doc, String... encabezado) {
        List<XWPFTable> todas = new ArrayList<>(doc.getTables());
        for (int i = 0; i < todas.size(); i++) {
            for (XWPFTableRow r : todas.get(i).getRows()) {
                for (XWPFTableCell c : r.getTableCells()) todas.addAll(c.getTables());
            }
        }
        return todas.stream()
                .filter(t -> !t.getRows().isEmpty() && celdas(t.getRow(0)).equals(List.of(encabezado)))
                .findFirst().orElseThrow(() -> new AssertionError("No hay una tabla con encabezado " + List.of(encabezado)));
    }

    private static List<String> celdas(XWPFTableRow fila) {
        return fila.getTableCells().stream().map(c -> c.getText().trim()).toList();
    }
}
