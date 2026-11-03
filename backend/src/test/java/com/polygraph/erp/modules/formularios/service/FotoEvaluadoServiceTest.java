package com.polygraph.erp.modules.formularios.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
