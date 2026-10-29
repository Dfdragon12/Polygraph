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
import com.polygraph.erp.shared.enums.TipoDatoFormulario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Genera un informe de Antecedentes completo con soportes reales; el Word queda en la ruta de
 *  -Dinforme.salida (por defecto target/informe_antecedentes_soportes.docx). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class GenerarInformeConSoportesTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private FormularioSeccionRepository seccionRepository;
    @Autowired private com.polygraph.erp.modules.riesgo.repository.CodigoCalificacionRepository codigoRepository;

    private List<FormularioSeccion> secciones;
    private final Map<Integer, String> valoresCampos = new HashMap<>();
    private final Map<Integer, List<FormularioFila>> filasPorSeccion = new HashMap<>();
    private final Map<Integer, Map<Integer, String>> valoresPorFila = new HashMap<>();
    private int siguienteFila = 1;

    private static byte[] captura(int w, int h, String titulo) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(0x1F3A5F));
        g.fillRect(0, 0, w, Math.min(70, h / 5));
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, Math.max(14, Math.min(28, w / 30))));
        g.drawString(titulo, 20, Math.min(45, h / 8));
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font("Arial", Font.PLAIN, Math.max(12, Math.min(22, w / 40))));
        int i = 1;
        for (int y = Math.min(110, h / 3); y < h - 20; y += 40, i++) {
            g.drawString("Resultado de la consulta · renglón " + i + " · SIN ANTECEDENTES VIGENTES", 30, y);
        }
        g.setColor(new Color(0xCC0000));
        g.drawRect(2, 2, w - 5, h - 5);
        g.dispose();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(img, "png", o);
        return o.toByteArray();
    }

    private static byte[] pdf(int paginas) throws Exception {
        try (var doc = new org.apache.pdfbox.pdmodel.PDDocument(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            for (int i = 1; i <= paginas; i++) {
                var pag = new org.apache.pdfbox.pdmodel.PDPage();
                doc.addPage(pag);
                try (var cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, pag)) {
                    cs.beginText();
                    cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD), 18);
                    cs.newLineAtOffset(80, 700);
                    cs.showText("Certificado en PDF - pagina " + i + " de " + paginas);
                    cs.endText();
                }
            }
            doc.save(o);
            return o.toByteArray();
        }
    }

    @Test
    void generar() throws Exception {
        TipoProgreso antecedentes = PlantillaAntecedentesPrueba.aplicarV207(jdbc, em);
        secciones = seccionRepository.findByTipoProgreso_IdTipoProgresoAndActivoTrueOrderByOrdenAsc(antecedentes.getIdTipoProgreso());
        // Los soportes de la plantilla del repo son texto: aquí se vuelven archivos.
        columna("ln_soporte").setTipoDato(TipoDatoFormulario.ARCHIVO);
        columna("li_soporte").setTipoDato(TipoDatoFormulario.ARCHIVO);
        columna("simit_comparendos").setTipoDato(TipoDatoFormulario.ARCHIVO); // campo suelto, fuera de tabla

        Map<String, byte[]> archivos = new LinkedHashMap<>();
        archivos.put("formularios/1/a_procuraduria.png", captura(1366, 768, "Procuraduría General de la Nación"));
        archivos.put("formularios/1/b_rnmc.png", captura(1366, 768, "Policía Nacional · RNMC"));
        archivos.put("formularios/1/c_recortada.png", captura(400, 150, "Contraloría"));
        archivos.put("formularios/1/d_vertical.png", captura(800, 2000, "SIMIT · estado de cuenta"));
        archivos.put("formularios/1/e_ofac.png", captura(1200, 700, "OFAC · lista internacional"));

        archivos.put("formularios/1/f_simit.png", captura(1100, 600, "SIMIT · comparendos"));
        archivos.put("formularios/1/g_certificado.pdf", pdf(2));
        campo("simit_comparendos", "[\"formularios/1/f_simit.png\"]");
        campo("ev_criticidad", "Estándar");
        campo("ev_conduccion", "No");
        campo("ev_estado_doc", "Vigente");
        campo("dec1_coincide", "Sí");
        campo("dec2_coincide", "Sí");
        campo("dec3_coincide", "Sí");
        campo("simit_ref", "LEG-17");
        campo("simit_multas", "2 pendientes");
        fila("listas_nacionales", Map.of("ln_fuente", "Procuraduría", "ln_resultado", "Sin novedad", "ln_ref", "—", "ln_observacion", "Sin sanciones",
                "ln_soporte", "[\"formularios/1/a_procuraduria.png\"]"));
        fila("listas_nacionales", Map.of("ln_fuente", "Medidas correctivas (RNMC)", "ln_resultado", "Con hallazgo", "ln_ref", "LEG-18", "ln_observacion", "Multa tipo 2 (2021)",
                "ln_soporte", "[\"formularios/1/b_rnmc.png\",\"formularios/1/d_vertical.png\"]"));
        fila("listas_nacionales", Map.of("ln_fuente", "Contraloría", "ln_resultado", "Sin novedad", "ln_ref", "—", "ln_observacion", "Sin responsabilidad fiscal",
                "ln_soporte", "[\"formularios/1/c_recortada.png\"]"));
        fila("listas_internacionales", Map.of("li_lista", "OFAC – Lista Clinton", "li_resultado", "Sin novedad", "li_ref", "—", "li_observacion", "Sin coincidencias",
                "li_soporte", "[\"formularios/1/e_ofac.png\",\"formularios/1/g_certificado.pdf\"]"));
        fila("procesos_privados", Map.of("pp_delito", "Hurto", "pp_estado", "Inactivo", "pp_calificacion", "LEG-19"));

        CandidatoInfoService candidatoInfo = mock(CandidatoInfoService.class);
        when(candidatoInfo.resolverUno(any(), anyString())).thenReturn("Laura Ejemplo Pérez");
        when(candidatoInfo.resolverUnoServicio(any(), anyString())).thenReturn("EST-2026-0001");
        when(candidatoInfo.resolverTablasFijas(any())).thenReturn(Map.of());
        AlmacenamientoDocumentosService almacenamiento = mock(AlmacenamientoDocumentosService.class);
        when(almacenamiento.cargarComoResource("antecedentes.docx")).thenReturn(new FileSystemResource(PlantillaAntecedentesPrueba.WORD));
        archivos.forEach((ruta, datos) -> when(almacenamiento.cargarComoResource(ruta)).thenReturn(new ByteArrayResource(datos)));

        ValoresPorClave.Entrada entrada = ValoresPorClave.armar(secciones, valoresCampos, filasPorSeccion, valoresPorFila);
        var catalogo = CalculoSubservicioService.catalogoDe(codigoRepository.findAll());
        CalculoFormulario calculo = new CalculoSubservicioService(() -> catalogo, () -> PlantillaAntecedentesPrueba.UMBRALES)
                .calcular(secciones, null, entrada.campos(), entrada.tablas()).orElseThrow();

        FormularioPlantillaWordService word = new FormularioPlantillaWordService(candidatoInfo, almacenamiento, codigoRepository);
        byte[] docx = word.generar(FormularioPlantillaConfig.builder().plantillaWordRuta("antecedentes.docx").build(), null, null, null,
                secciones, valoresCampos, filasPorSeccion, valoresPorFila, true, null, null, calculo);
        Path salida = Path.of(System.getProperty("informe.salida", "target/informe_antecedentes_soportes.docx"));
        Files.createDirectories(salida.toAbsolutePath().getParent());
        Files.write(salida, docx);
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
}
