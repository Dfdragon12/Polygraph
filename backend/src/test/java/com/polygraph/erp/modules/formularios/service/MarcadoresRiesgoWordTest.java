package com.polygraph.erp.modules.formularios.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.verron.officestamper.preset.OfficeStampers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Fase E (docs/modelo-calificacion-riesgo.md §7): los marcadores de riesgo se resuelven con el
 *  MISMO motor de plantillas de producción (office-stamper), y siempre traen valor (vacío si no
 *  aplica) — un marcador de riesgo nunca debe romper la generación del documento. */
@DisplayName("Marcadores de riesgo en el Word")
class MarcadoresRiesgoWordTest {

    @Test
    @DisplayName("${riesgo['nivel']}, ${riesgo['puntaje']} y ${calificacion[...]} se reemplazan (caso 2: MEDIO / 31,0)")
    void marcadoresDeRiesgoSeReemplazan() throws Exception {
        String texto = textoGenerado(new ContextoPlantillaWord(Map.of(), Map.of(), Map.of(),
                Map.of("nivel", "MEDIO", "puntaje", "31,0"), Map.of("nivel", "ALTO", "puntaje", "100,0")));

        assertThat(texto).contains("Nivel: MEDIO").contains("Puntaje: 31,0")
                .contains("Subproceso: ALTO (100,0)")
                .doesNotContain("${");
    }

    @Test
    @DisplayName("Sin cálculo (constructor de siempre): los marcadores quedan vacíos y el documento se genera igual")
    void sinCalculo_quedanVacios() throws Exception {
        String texto = textoGenerado(new ContextoPlantillaWord(Map.of(), Map.of(), Map.of()));

        assertThat(texto).contains("Nivel: ").doesNotContain("${");
    }

    private static String textoGenerado(ContextoPlantillaWord contexto) throws Exception {
        byte[] plantilla;
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Nivel: ${riesgo['nivel']}");
            doc.createParagraph().createRun().setText("Puntaje: ${riesgo['puntaje']}");
            doc.createParagraph().createRun().setText("Subproceso: ${calificacion['nivel']} (${calificacion['puntaje']})");
            doc.write(out);
            plantilla = out.toByteArray();
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (InputStream in = new ByteArrayInputStream(plantilla)) {
            OfficeStampers.docxStamper().stamp(in, contexto, salida);
        }
        try (XWPFDocument generado = new XWPFDocument(new ByteArrayInputStream(salida.toByteArray()))) {
            StringBuilder sb = new StringBuilder();
            generado.getParagraphs().forEach(p -> sb.append(p.getText()).append('\n'));
            return sb.toString();
        }
    }
}
