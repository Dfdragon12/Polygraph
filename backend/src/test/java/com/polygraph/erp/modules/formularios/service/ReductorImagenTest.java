package com.polygraph.erp.modules.formularios.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ReductorImagenTest {

    private static byte[] png(int w, int h, boolean ruido) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(7);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, ruido ? r.nextInt(0xFFFFFF) : 0x336699);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(img, "png", o);
        return o.toByteArray();
    }

    @Test
    void unaFotoGrandeSeReduceA1600YSeAchica() throws Exception {
        byte[] grande = png(3200, 2400, true);
        byte[] r = ReductorImagen.paraInforme(grande);
        BufferedImage salida = ImageIO.read(new ByteArrayInputStream(r));
        assertThat(Math.max(salida.getWidth(), salida.getHeight())).isEqualTo(1600);
        assertThat(salida.getHeight()).isEqualTo(1200);
        assertThat(r.length).isLessThan(grande.length);
    }

    @Test
    void unaFotoLivianaSeDevuelveIgual() throws Exception {
        byte[] chica = png(400, 300, false);
        assertThat(ReductorImagen.paraInforme(chica)).isSameAs(chica);
    }

    @Test
    void loQueNoEsImagenSeDevuelveIgual() {
        byte[] basura = {1, 2, 3, 4};
        assertThat(ReductorImagen.paraInforme(basura)).isSameAs(basura);
    }
}
