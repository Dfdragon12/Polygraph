package com.polygraph.erp.modules.formularios.service;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STUnderline;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Soportes (imágenes) de las tablas del informe: en la celda va solo un enlace "Ver soporte N"
 *  que salta a la imagen, y las imágenes completas van juntas al final del documento, en un
 *  anexo a ancho completo. Se arma DESPUÉS de generar el Word, sin tocar la plantilla. */
final class SoportesAnexoWord {

    /** Una imagen del anexo; numero = el N de "Ver soporte N". */
    record Soporte(int numero, String titulo, byte[] datos, boolean png) {
    }

    private static final Pattern ENLACE = Pattern.compile("Ver soporte (\\d+)");
    private static final String NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final double CM = 72.0 / 2.54; // puntos por centímetro
    /** Tamaño fijo: no depende de los DPI del archivo (también agranda las imágenes pequeñas). */
    static final double ANCHO_CM = 17.0;
    static final double ALTO_MAX_CM = 21.0;
    private static final int ANCHO_MAX_PX = 2000;
    private static final int MAX_PAGINAS_PDF = 30;

    private SoportesAnexoWord() {
    }

    static String textoEnlace(int numero) {
        return "Ver soporte " + numero;
    }

    /** Ancho máximo de pixeles: una captura enorme no debe volver pesado el Word ni el PDF. */
    static byte[] reducirSiHaceFalta(byte[] datos, boolean png) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(datos));
        if (img == null || img.getWidth() <= ANCHO_MAX_PX) return datos;
        int alto = (int) Math.round(img.getHeight() * (double) ANCHO_MAX_PX / img.getWidth());
        BufferedImage destino = new BufferedImage(ANCHO_MAX_PX, alto, png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, ANCHO_MAX_PX, alto, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(destino, png ? "png" : "jpg", out);
        return out.toByteArray();
    }

    /** Cada página de un PDF como PNG a 150 ppp (tope de páginas: un PDF enorme no debe volver
     *  inmanejable el informe). */
    static List<byte[]> paginasPdf(byte[] pdf) throws Exception {
        List<byte[]> paginas = new ArrayList<>();
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            org.apache.pdfbox.rendering.PDFRenderer render = new org.apache.pdfbox.rendering.PDFRenderer(doc);
            int total = Math.min(doc.getNumberOfPages(), MAX_PAGINAS_PDF);
            for (int i = 0; i < total; i++) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(render.renderImageWithDPI(i, 150), "png", out);
                paginas.add(reducirSiHaceFalta(out.toByteArray(), true));
            }
        }
        return paginas;
    }

    /** Convierte cada "Ver soporte N" en enlace interno y agrega el anexo con las imágenes. */
    static byte[] aplicar(byte[] docx, List<Soporte> soportes) throws Exception {
        if (soportes == null || soportes.isEmpty()) return docx;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            enlazarParrafos(doc.getParagraphs(), soportes);
            for (XWPFTable t : doc.getTables()) enlazarTabla(t, soportes);
            agregarAnexo(doc, soportes);
            doc.write(salida);
            return salida.toByteArray();
        }
    }

    private static void enlazarTabla(XWPFTable tabla, List<Soporte> soportes) {
        for (XWPFTableRow fila : tabla.getRows()) {
            for (XWPFTableCell celda : fila.getTableCells()) {
                enlazarParrafos(celda.getParagraphs(), soportes);
                for (XWPFTable anidada : celda.getTables()) enlazarTabla(anidada, soportes);
            }
        }
    }

    private static void enlazarParrafos(List<XWPFParagraph> parrafos, List<Soporte> soportes) {
        for (XWPFParagraph p : parrafos) {
            for (XWPFRun run : new ArrayList<>(p.getRuns())) {
                String texto = run.getText(0);
                if (texto == null || !ENLACE.matcher(texto).find()) continue;
                reemplazarRun(run.getCTR(), texto, soportes);
            }
        }
    }

    /** Parte el run en trozos (texto normal / enlace) y los pone en su lugar. */
    private static void reemplazarRun(CTR original, String texto, List<Soporte> soportes) {
        List<String[]> trozos = new ArrayList<>(); // {texto, numero|null}
        Matcher m = ENLACE.matcher(texto);
        int pos = 0;
        while (m.find()) {
            int numero = Integer.parseInt(m.group(1));
            if (soportes.stream().noneMatch(s -> s.numero() == numero)) continue;
            if (m.start() > pos) trozos.add(new String[]{texto.substring(pos, m.start()), null});
            trozos.add(new String[]{m.group(), m.group(1)});
            pos = m.end();
        }
        if (trozos.isEmpty()) return;
        if (pos < texto.length()) trozos.add(new String[]{texto.substring(pos), null});

        try (XmlCursor destino = original.newCursor()) {
            for (String[] trozo : trozos) {
                if (trozo[1] == null) {
                    copiarRun(xmlRun(original, trozo[0], false), destino);
                    continue;
                }
                XmlObject enlace = XmlObject.Factory.parse(
                        "<w:hyperlink xmlns:w=\"" + NS_W + "\" w:anchor=\"soporte_" + trozo[1] + "\" w:history=\"1\"/>");
                try (XmlCursor origen = enlace.newCursor()) {
                    origen.toFirstChild();
                    origen.copyXml(destino);
                }
                try (XmlCursor dentro = destino.newCursor()) {
                    dentro.toPrevSibling(); // el hyperlink recién copiado
                    dentro.toEndToken();    // insertar al final de su contenido
                    copiarRun(xmlRun(original, trozo[0], true), dentro);
                }
            }
            destino.removeXml(); // el run original
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo crear el enlace al soporte: " + e.getMessage(), e);
        }
    }

    /** Un run nuevo con el formato del original (rPr) y este texto; el enlace lo pinta azul y
     *  subrayado. Se arma como XML explícito: CTR.copy() pierde el elemento w:r. */
    private static String xmlRun(CTR original, String texto, boolean enlace) throws Exception {
        org.apache.xmlbeans.XmlOptions fuera = new org.apache.xmlbeans.XmlOptions().setSaveOuter();
        CTRPr rpr = CTRPr.Factory.parse(original.isSetRPr() ? original.getRPr().xmlText(fuera)
                : "<w:rPr xmlns:w=\"" + NS_W + "\"/>");
        if (enlace) {
            while (rpr.sizeOfColorArray() > 0) rpr.removeColor(0);
            while (rpr.sizeOfUArray() > 0) rpr.removeU(0);
            rpr.addNewColor().setVal("0563C1");
            rpr.addNewU().setVal(STUnderline.SINGLE);
        }
        String escapado = texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<w:r xmlns:w=\"" + NS_W + "\">" + rpr.xmlText(fuera) + "<w:t xml:space=\"preserve\">" + escapado + "</w:t></w:r>";
    }

    /** Inserta el run ANTES de la posición del cursor. */
    private static void copiarRun(String xml, XmlCursor destino) throws Exception {
        XmlObject doc = XmlObject.Factory.parse(xml);
        try (XmlCursor origen = doc.newCursor()) {
            origen.toFirstChild();
            origen.copyXml(destino);
        }
    }

    private static void agregarAnexo(XWPFDocument doc, List<Soporte> soportes) throws Exception {
        XWPFParagraph titulo = doc.createParagraph();
        titulo.setPageBreak(true);
        XWPFRun t = titulo.createRun();
        t.setBold(true);
        t.setFontSize(14);
        t.setText("Soportes de las consultas");

        long idMarcador = 9000;
        for (Soporte s : soportes) {
            XWPFParagraph encabezado = doc.createParagraph();
            encabezado.setSpacingBefore(240);
            encabezado.setKeepNext(true);
            CTP ctp = encabezado.getCTP();
            CTBookmark inicio = ctp.addNewBookmarkStart();
            inicio.setId(BigInteger.valueOf(idMarcador));
            inicio.setName("soporte_" + s.numero());
            XWPFRun r = encabezado.createRun();
            r.setBold(true);
            r.setText("Soporte " + s.numero() + (s.titulo() == null || s.titulo().isBlank() ? "" : " · " + s.titulo()));
            ctp.addNewBookmarkEnd().setId(BigInteger.valueOf(idMarcador));
            idMarcador++;

            BufferedImage img = ImageIO.read(new ByteArrayInputStream(s.datos()));
            double ancho = ANCHO_CM * CM;
            double alto = img == null ? ancho * 0.6 : ancho * img.getHeight() / img.getWidth();
            double altoMax = ALTO_MAX_CM * CM;
            if (alto > altoMax) {
                ancho = ancho * altoMax / alto;
                alto = altoMax;
            }
            XWPFParagraph imagen = doc.createParagraph();
            imagen.setAlignment(ParagraphAlignment.CENTER);
            imagen.createRun().addPicture(new ByteArrayInputStream(s.datos()),
                    s.png() ? Document.PICTURE_TYPE_PNG : Document.PICTURE_TYPE_JPEG,
                    "soporte_" + s.numero() + (s.png() ? ".png" : ".jpg"), Units.toEMU(ancho), Units.toEMU(alto));
        }
    }
}
