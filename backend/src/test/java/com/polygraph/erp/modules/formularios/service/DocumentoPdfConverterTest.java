package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.shared.exceptions.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** DocumentoPdfConverter llama al binario real de LibreOffice ("soffice") — estas pruebas
 *  necesitan tenerlo instalado en la máquina donde corren. Si no está (por ejemplo, en un CI que
 *  todavía no lo tiene configurado), se SALTAN en vez de fallar — el @BeforeAll lo detecta una
 *  sola vez para toda la clase, no en cada prueba. En este proyecto, application-dev.yml ya
 *  documenta la ruta esperada en Windows; ver ese archivo si esto se salta sin que debería. */
@DisplayName("DocumentoPdfConverter — Conversión de .docx a PDF (LibreOffice headless)")
class DocumentoPdfConverterTest {

    private static boolean sofficeDisponible;
    // Cuál de los dos binarios probados funcionó — en Windows normalmente NO es "soffice" (no
    // queda en el PATH del proceso que corre las pruebas, aunque sí esté instalado), así que hay
    // que fijar el mismo binario que resultó disponible en el converter de cada prueba: si no, el
    // converter usa su valor por defecto ("soffice", literal en el campo) y falla con
    // "CreateProcess error=2" aunque LibreOffice sí esté instalado y detectado arriba.
    private static String binarioResuelto;

    private final DocumentoPdfConverter converter = new DocumentoPdfConverter();

    @BeforeAll
    static void detectarLibreOffice() {
        if (probarSoffice("soffice")) {
            binarioResuelto = "soffice";
        } else if (probarSoffice("C:\\Program Files\\LibreOffice\\program\\soffice.exe")) {
            binarioResuelto = "C:\\Program Files\\LibreOffice\\program\\soffice.exe";
        }
        sofficeDisponible = binarioResuelto != null;
    }

    @BeforeEach
    void fijarBinarioDetectado() {
        if (sofficeDisponible) {
            ReflectionTestUtils.setField(converter, "binarioLibreOffice", binarioResuelto);
        }
    }

    private static boolean probarSoffice(String binario) {
        try {
            // Sin -env:UserInstallation, esta prueba usaría el perfil compartido por defecto de
            // LibreOffice — si otra corrida reciente (esta misma prueba, o convertir() de otra
            // prueba) dejó ese perfil bloqueado, este --version se queda esperando el lock y
            // termina agotando el timeout, marcando LibreOffice como "no disponible" aunque sí lo
            // esté (flakiness confirmada corriendo esta clase varias veces seguidas). Con un
            // perfil aislado por invocación, igual que hace DocumentoPdfConverter.convertir(), no
            // hay con qué contienda.
            java.nio.file.Path perfil = java.nio.file.Files.createTempDirectory("polygraph-pdf-probe-");
            try {
                Process p = new ProcessBuilder(
                        binario, "--headless", "--norestore", "--nologo", "--nofirststartwizard",
                        "-env:UserInstallation=" + perfil.toUri(), "--version").start();
                boolean termino = p.waitFor(45, java.util.concurrent.TimeUnit.SECONDS);
                if (!termino) p.destroyForcibly();
                return termino && p.exitValue() == 0;
            } finally {
                try (var flujo = java.nio.file.Files.walk(perfil)) {
                    flujo.sorted(java.util.Comparator.reverseOrder())
                            .forEach(ruta -> { try { java.nio.file.Files.deleteIfExists(ruta); } catch (java.io.IOException ignored) { } });
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] docxDePrueba() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph parrafo = doc.createParagraph();
            XWPFRun run = parrafo.createRun();
            run.setText("Formulario de prueba — Polygraph Service");
            run.setBold(true);

            XWPFTable tabla = doc.createTable(2, 2);
            tabla.getRow(0).getCell(0).setText("Campo");
            tabla.getRow(0).getCell(1).setText("Valor");
            tabla.getRow(1).getCell(0).setText("Nombre");
            tabla.getRow(1).getCell(1).setText("Evaluado de ejemplo");

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            doc.write(salida);
            return salida.toByteArray();
        }
    }

    private String textoDe(byte[] pdf) throws Exception {
        try (var doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    @DisplayName("Convierte un .docx válido en un PDF con el mismo contenido (párrafo y celda de tabla)")
    void convierteDocxValidoAPdf() throws Exception {
        assumeTrue(sofficeDisponible, "LibreOffice no está instalado en esta máquina — prueba saltada");

        byte[] pdf = converter.convertir(docxDePrueba());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        String texto = textoDe(pdf);
        assertThat(texto).contains("Formulario de prueba");
        assertThat(texto).contains("Evaluado de ejemplo"); // celda de tabla
    }

    /** No se prueba con un .docx "corrupto" (bytes al azar, texto plano, etc.): se confirmó
     *  corriendo esta prueba contra LibreOffice de verdad que su autodetector de formato es lo
     *  bastante permisivo para interpretar casi cualquier bytes como texto plano y de todos modos
     *  producir un PDF (aunque sea de basura) — nunca lanza error por el contenido en sí. El caso
     *  real que sí falla, y el que de verdad motivó el manejo de ApiException en esta clase, es
     *  LibreOffice mal instalado/configurado — por eso se simula apuntando a un binario que no
     *  existe, en vez de intentar fabricar un .docx que lo haga fallar por contenido. */
    @Test
    @DisplayName("Un binario de LibreOffice mal configurado lanza ApiException en vez de reventar sin control")
    void binarioMalConfiguradoLanzaApiException() throws Exception {
        ReflectionTestUtils.setField(converter, "binarioLibreOffice", "C:\\ruta\\que-no-existe\\soffice.exe");

        assertThatThrownBy(() -> converter.convertir(docxDePrueba()))
                .isInstanceOf(ApiException.class);
    }

    /** A diferencia del motor anterior (Apache FOP vía docx4j, ver el historial de este archivo),
     *  LibreOffice sí sabe dibujar cuadros de texto — esto es justo lo que motivó el cambio de
     *  motor (ver MarcadoresEnCuadroDeTextoTest para la parte de que el marcador se reemplaza bien
     *  en el .docx; esta prueba confirma que además sí llega al PDF). */
    @Test
    @DisplayName("El contenido de un cuadro de texto SÍ aparece en el PDF (a diferencia del motor anterior)")
    void cuadroDeTextoApareceEnElPdf() throws Exception {
        assumeTrue(sofficeDisponible, "LibreOffice no está instalado en esta máquina — prueba saltada");

        byte[] docxConCuadro = docxConParrafoYCuadroDeTexto();

        byte[] pdf = converter.convertir(docxConCuadro);
        String texto = textoDe(pdf);

        assertThat(texto).contains("TEXTO-EN-PARRAFO-NORMAL");
        assertThat(texto).contains("TEXTO-EN-CUADRO-DE-TEXTO");
    }

    private byte[] docxConParrafoYCuadroDeTexto() throws Exception {
        byte[] base;
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph parrafo = doc.createParagraph();
            XWPFRun run = parrafo.createRun();
            run.setText("TEXTO-EN-PARRAFO-NORMAL");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            base = out.toByteArray();
        }

        String parrafoConCuadro =
                "<w:p><w:r><w:pict>"
                + "<v:shape xmlns:v=\"urn:schemas-microsoft-com:vml\" id=\"TextBox1\" "
                + "style=\"position:absolute;width:200pt;height:40pt\" filled=\"f\" stroked=\"t\">"
                + "<v:textbox><w:txbxContent><w:p><w:r><w:t>TEXTO-EN-CUADRO-DE-TEXTO"
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
}
