package com.polygraph.erp.modules.formularios.service;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import pro.verron.officestamper.preset.OfficeStampers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Secciones opcionales en el Word (V212): el bloque marcado con repeatDocPart(secciones['clave'])
 *  sale UNA vez con todos sus marcadores (también un repeatTableRow adentro) si la sección va, y
 *  desaparece completo si quien diligencia la quitó. Lo de fuera del bloque no cambia. */
class SeccionesOpcionalesWordTest {

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    @Test
    void seccionIncluidaSaleUnaVezConSusMarcadores() throws Exception {
        String texto = generar(Set.of());
        assertThat(texto).contains("Inicio Juan", "Hallazgos con impacto", "LEG-01", "LEG-05", "Cierre Juan", "Final");
        assertThat(texto.split("Hallazgos con impacto", -1)).hasSize(2); // una sola vez
    }

    @Test
    void seccionQuitadaDesapareceCompleta() throws Exception {
        String texto = generar(Set.of("hallazgos"));
        assertThat(texto).contains("Inicio Juan", "Final");
        assertThat(texto).doesNotContain("Hallazgos con impacto", "LEG-01", "Cierre");
    }

    @Test
    void claveQueNoExisteNoBorraNada() {
        ContextoPlantillaWord contexto = contexto(Set.of("hallazgos"));
        assertThat(contexto.getSecciones().get("otra")).containsExactly(contexto);
        assertThat(contexto.getSecciones().get("hallazgos")).isEmpty();
    }

    private static ContextoPlantillaWord contexto(Set<String> omitidas) {
        Map<String, Object> campos = new HashMap<>(Map.of("nombre", "Juan"));
        Map<String, List<Map<String, Object>>> tablas = new HashMap<>();
        tablas.put("hallazgos", List.of(Map.of("ref", "LEG-01"), Map.of("ref", "LEG-05")));
        return new ContextoPlantillaWord(Map.of(), campos, tablas).conSeccionesOmitidas(omitidas);
    }

    private static String generar(Set<String> omitidas) throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        OfficeStampers.docxStamper().stamp(new ByteArrayInputStream(plantilla()), contexto(omitidas), salida);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(salida.toByteArray()));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private static String parrafo(String texto) {
        return "<w:p><w:r><w:t xml:space=\"preserve\">" + texto + "</w:t></w:r></w:p>";
    }

    /** Word mínimo: texto, bloque de la sección (título + tabla repetida + texto) y texto final. */
    private static byte[] plantilla() throws Exception {
        String documento = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"" + W + "\"><w:body>"
                + parrafo("Inicio ${campos['nombre']}")
                + "<w:p><w:commentRangeStart w:id=\"0\"/><w:r><w:t>Hallazgos con impacto</w:t></w:r></w:p>"
                + "<w:tbl><w:tblPr/><w:tblGrid><w:gridCol w:w=\"4000\"/></w:tblGrid>"
                + "<w:tr><w:tc><w:p><w:commentRangeStart w:id=\"1\"/><w:r><w:t>${['ref']}</w:t></w:r>"
                + "<w:commentRangeEnd w:id=\"1\"/><w:r><w:commentReference w:id=\"1\"/></w:r></w:p></w:tc></w:tr></w:tbl>"
                + "<w:p><w:r><w:t xml:space=\"preserve\">Cierre ${campos['nombre']}</w:t></w:r>"
                + "<w:commentRangeEnd w:id=\"0\"/><w:r><w:commentReference w:id=\"0\"/></w:r></w:p>"
                + parrafo("Final")
                + "</w:body></w:document>";
        String comentarios = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:comments xmlns:w=\"" + W + "\">"
                + "<w:comment w:id=\"0\" w:author=\"admin\">" + parrafo("repeatDocPart(secciones['hallazgos'])") + "</w:comment>"
                + "<w:comment w:id=\"1\" w:author=\"admin\">" + parrafo("repeatTableRow(tablas['hallazgos'])") + "</w:comment>"
                + "</w:comments>";
        Map<String, String> partes = new java.util.LinkedHashMap<>();
        partes.put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "<Override PartName=\"/word/comments.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml\"/>"
                + "</Types>");
        partes.put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                + "</Relationships>");
        partes.put("word/_rels/document.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments\" Target=\"comments.xml\"/>"
                + "</Relationships>");
        partes.put("word/document.xml", documento);
        partes.put("word/comments.xml", comentarios);
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(zip)) {
            for (var e : partes.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return zip.toByteArray();
    }
}
