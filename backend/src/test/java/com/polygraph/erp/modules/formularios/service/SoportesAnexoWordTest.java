package com.polygraph.erp.modules.formularios.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SoportesAnexoWordTest {

    private static byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", o);
        return o.toByteArray();
    }

    @Test
    void celdaQuedaComoEnlaceEImagenVaAlFinal() throws Exception {
        ByteArrayOutputStream base = new ByteArrayOutputStream();
        try (XWPFDocument d = new XWPFDocument()) {
            XWPFTable t = d.createTable(1, 1);
            t.getRow(0).getCell(0).setText("Ver soporte 1, Ver soporte 2");
            d.write(base);
        }
        byte[] docx = SoportesAnexoWord.aplicar(base.toByteArray(), List.of(
                new SoportesAnexoWord.Soporte(1, "Procuraduría", png(400, 150), true),
                new SoportesAnexoWord.Soporte(2, "Vertical", png(800, 2000), true)));
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(docx))) {
            XWPFParagraph celda = d.getTables().get(0).getRow(0).getCell(0).getParagraphs().get(0);
            String xml = celda.getCTP().xmlText();
            assertTrue(xml.contains("soporte_1") && xml.contains("soporte_2"), xml);
            assertTrue(xml.contains("Ver soporte 1") && xml.contains("Ver soporte 2"), "el texto del enlace debe conservarse: " + xml);
            assertEquals(2, d.getAllPictures().size());
            String cuerpo = d.getDocument().getBody().xmlText();
            assertTrue(cuerpo.contains("w:bookmarkStart") && cuerpo.contains("Soportes de las consultas"));
            // imagen vertical: 21 cm de alto = 7.560.000 EMU
            assertTrue(cuerpo.contains("cy=\"7560000\""), "alto máximo 21 cm");
        }
    }
}
