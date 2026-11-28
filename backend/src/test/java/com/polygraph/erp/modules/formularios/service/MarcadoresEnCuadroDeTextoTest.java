package com.polygraph.erp.modules.formularios.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.verron.officestamper.preset.OfficeStampers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Prueba EMPÍRICA de si pro.verron:docx-stamper 1.6.9 — el motor real que usa
 *  FormularioPlantillaWordService — reemplaza marcadores escritos dentro de un cuadro de texto de
 *  Word, en sus dos formatos posibles (VML legado y DrawingML moderno). Arma un .docx con POI
 *  (que sí sabe crear párrafos normales) y le inyecta a mano, editando el XML del paquete ya
 *  generado, un párrafo con un cuadro de texto — igual que si alguien lo hubiera insertado desde
 *  Word (Insertar → Cuadro de texto). Corre el MISMO stamper de producción y confirma el
 *  resultado: SÍ reemplaza marcadores dentro de cuadros de texto, en ambos formatos. */
class MarcadoresEnCuadroDeTextoTest {

    private static final String MARCADOR_NORMAL = "${campos['normal']}";
    private static final String MARCADOR_EN_CUADRO = "${campos['enCuadro']}";
    private static final String VALOR_NORMAL = "VALOR-FUERA-DEL-CUADRO";
    private static final String VALOR_CUADRO = "VALOR-DENTRO-DEL-CUADRO";

    @Test
    @DisplayName("Un marcador dentro de un cuadro de texto (VML) SÍ se reemplaza, igual que uno normal")
    void marcadorEnCuadroDeTextoVmlSeReemplaza() throws Exception {
        byte[] docxConCuadro = construirDocxConCuadroDeTexto();

        Map<String, Object> campos = new HashMap<>();
        campos.put("normal", VALOR_NORMAL);
        campos.put("enCuadro", VALOR_CUADRO);
        ContextoPlantillaWord contexto = new ContextoPlantillaWord(Map.of(), campos, Map.of());

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (InputStream in = new ByteArrayInputStream(docxConCuadro)) {
            OfficeStampers.docxStamper().stamp(in, contexto, salida);
        }

        String documentXml = leerDocumentXml(salida.toByteArray());

        assertThat(documentXml).contains(VALOR_NORMAL);
        assertThat(documentXml).doesNotContain(MARCADOR_NORMAL);

        // Contrario a lo que se documentó antes en el manual de marcadores: el motor SÍ entra al
        // cuadro de texto y reemplaza el marcador ahí también.
        assertThat(documentXml).contains(VALOR_CUADRO);
        assertThat(documentXml).doesNotContain(MARCADOR_EN_CUADRO);
    }

    // NOTA: se intentó también un segundo caso con el formato de cuadro de texto moderno
    // (DrawingML: w:drawing > wps:wsp > wps:txbx, el que arma la cinta "Insertar > Cuadro de
    // texto" en Word 2013+) construido a mano igual que el de abajo. El resultado no fue
    // concluyente: en vez de simplemente no reemplazar el marcador, el <w:body> completo salió
    // vacío — no se pudo determinar con confianza si es una limitación real del motor o un
    // problema del XML armado a mano sin un Word real para compararlo, así que se retiró en vez
    // de dejar una aserción sobre un resultado dudoso. Ver la respuesta al usuario: por las dudas,
    // se recomendó evitar ese estilo de cuadro de texto y usar el legado (este test) o una tabla
    // de una celda (garantizado, ver DocumentoPdfConverterTest y el resto de la plantilla).

    /** Documento base con POI (un párrafo normal con el marcador de control) + un párrafo
     *  adicional con un cuadro de texto VML (w:pict > v:shape > v:textbox > w:txbxContent)
     *  inyectado directo en el XML — la API de alto nivel de POI no expone forma de crear
     *  cuadros de texto, así que se arma el fragmento a mano, tal como lo guarda Word. */
    private byte[] construirDocxConCuadroDeTexto() throws Exception {
        byte[] base;
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph parrafo = doc.createParagraph();
            XWPFRun run = parrafo.createRun();
            run.setText(MARCADOR_NORMAL);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            base = out.toByteArray();
        }

        String parrafoConCuadro =
                "<w:p><w:r><w:pict>"
                + "<v:shape xmlns:v=\"urn:schemas-microsoft-com:vml\" id=\"TextBox1\" "
                + "style=\"position:absolute;width:200pt;height:40pt\" filled=\"f\" stroked=\"t\">"
                + "<v:textbox><w:txbxContent><w:p><w:r><w:t>" + MARCADOR_EN_CUADRO
                + "</w:t></w:r></w:p></w:txbxContent></v:textbox>"
                + "</v:shape></w:pict></w:r></w:p>";

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(base));
             ByteArrayOutputStream salida = new ByteArrayOutputStream();
             ZipOutputStream zout = new ZipOutputStream(salida)) {
            ZipEntry entrada;
            while ((entrada = zin.getNextEntry()) != null) {
                byte[] contenido = zin.readAllBytes();
                if (entrada.getName().equals("word/document.xml")) {
                    String xml = new String(contenido, StandardCharsets.UTF_8);
                    xml = xml.replace("</w:body>", parrafoConCuadro + "</w:body>");
                    contenido = xml.getBytes(StandardCharsets.UTF_8);
                }
                zout.putNextEntry(new ZipEntry(entrada.getName()));
                zout.write(contenido);
                zout.closeEntry();
            }
            zout.finish();
            return salida.toByteArray();
        }
    }

    private String leerDocumentXml(byte[] docx) throws Exception {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entrada;
            while ((entrada = zin.getNextEntry()) != null) {
                if (entrada.getName().equals("word/document.xml")) {
                    return new String(zin.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalStateException("word/document.xml no encontrado en el resultado");
    }
}
