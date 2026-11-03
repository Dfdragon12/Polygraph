package com.polygraph.erp.modules.formularios.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** PF-06: la foto del informe sale siempre en proporción vertical 3:4, venga como venga de la cámara. */
class FotoEvaluadoServiceTest {

    private static BufferedImage recortada(int ancho, int alto) throws Exception {
        byte[] jpg = FotoEvaluadoService.recortar34(new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB));
        return ImageIO.read(new ByteArrayInputStream(jpg));
    }

    @Test
    void imagenHorizontalSeRecortaDeLosLados() throws Exception {
        BufferedImage r = recortada(1280, 720);
        assertEquals(540, r.getWidth());
        assertEquals(720, r.getHeight());
    }

    @Test
    void imagenMuyAltaSeRecortaArribaYAbajo() throws Exception {
        BufferedImage r = recortada(600, 1200);
        assertEquals(600, r.getWidth());
        assertEquals(800, r.getHeight());
    }

    @Test
    void imagenYa34QuedaIgual() throws Exception {
        BufferedImage r = recortada(480, 640);
        assertEquals(480, r.getWidth());
        assertEquals(640, r.getHeight());
    }

    private static byte[] imagen(String formato) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(60, 80, BufferedImage.TYPE_INT_RGB), formato, out);
        return out.toByteArray();
    }

    /** FA-01: el tipo se decide por los bytes, no por el nombre ni por lo que declara el navegador. */
    @Test
    void reconoceJpgYPngReales() throws Exception {
        assertEquals("jpg", FotoEvaluadoService.tipoReal(imagen("jpg")));
        assertEquals("png", FotoEvaluadoService.tipoReal(imagen("png")));
    }

    @Test
    void rechazaLoQueNoEsJpgNiPng() throws Exception {
        assertNull(FotoEvaluadoService.tipoReal("%PDF-1.7 esto es un pdf con extension .jpg".getBytes(StandardCharsets.UTF_8)));
        assertNull(FotoEvaluadoService.tipoReal(imagen("gif")));
        assertNull(FotoEvaluadoService.tipoReal(new byte[0]));
        byte[] cortada = imagen("jpg");
        assertNull(FotoEvaluadoService.tipoReal(java.util.Arrays.copyOf(cortada, 6)));
    }
}
