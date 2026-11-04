package com.polygraph.erp.modules.formularios.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.junit.jupiter.api.Test;
import pro.verron.officestamper.preset.OfficeStampers;

/** VD-03: la plantilla de la visita domiciliaria numera el protocolo con marcadores, no con números fijos. */
class NumeracionProtocoloWordTest {

    private static final Path PLANTILLA = Path.of("../docs/Visita Domiciliaria presencial/1_Formato_Visita_Domiciliaria.docx");

    private static List<String> textos(boolean sindical) throws Exception {
        Map<String, Object> campos = new HashMap<>(NumeracionProtocolo.marcadores(sindical));
        Map<String, List<Map<String, Object>>> tablas = new HashMap<>();
        tablas.put("ps_sindical", sindical ? List.of(new HashMap<>(Map.of("ps_10", "No", "ps_10_det", "—", "ps_10_ref", ""))) : List.of());
        for (String k : List.of("salud", "rutina", "virtual", "vehicular", "oea", "oea_bd", "teletrabajo", "fotos_vehicular", "fotos_teletrabajo")) tablas.put(k, List.of());
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (InputStream in = Files.newInputStream(PLANTILLA)) {
            OfficeStampers.docxStamper().stamp(in, new ContextoPlantillaWord(Map.of(), campos, tablas), salida);
        }
        List<String> r = new java.util.ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(salida.toByteArray()))) {
            doc.getParagraphs().forEach(p -> r.add(p.getText()));
            doc.getTables().forEach(t -> t.getRows().forEach(f -> f.getTableCells().forEach(c -> c.getParagraphs().forEach((XWPFParagraph p) -> r.add(p.getText())))));
        }
        return r;
    }

    @Test
    void sindicalApagadaNumeraSinSaltos() throws Exception {
        List<String> t = textos(false);
        assertThat(t).anyMatch(s -> s.startsWith("9. ¿Usted o algún miembro de su familia han tenido vínculos"));
        assertThat(t).anyMatch(s -> s.startsWith("10. ¿Algún miembro de su familia se encuentra radicado fuera del país?"));
        assertThat(t).anyMatch(s -> s.startsWith("16. ¿Usted consume o ha consumido sustancias psicoactivas?"));
        assertThat(t).noneMatch(s -> s.startsWith("17. ¿Usted consume"));
        assertThat(t).noneMatch(s -> s.contains("${"));
        assertThat(t).anyMatch(s -> s.contains("Las preguntas 1, 2, 3, 12 y 13 se comparan"));
    }
}
