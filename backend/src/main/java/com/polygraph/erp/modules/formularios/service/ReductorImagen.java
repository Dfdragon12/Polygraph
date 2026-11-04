package com.polygraph.erp.modules.formularios.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * VD-07 · Reduce una foto para el informe Word SIN tocar el original guardado: lado mayor de máximo 1600 px y JPG de calidad 0,85, igual que el navegador.
 * Sirve para las fotos que ya estaban guardadas con su tamaño de origen (algunas de 8 MB). Una imagen que ya cabe se devuelve tal cual. Pura.
 */
public final class ReductorImagen {

    public static final int MAX_LADO = 1600;
    public static final int UMBRAL_BYTES = 500 * 1024;
    private static final float CALIDAD = 0.85f;

    private ReductorImagen() {}

    /** Devuelve los bytes a incrustar: los mismos si ya es liviana, o un JPG reducido. Si no se puede leer, devuelve los originales. */
    public static byte[] paraInforme(byte[] original) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(original));
            if (img == null) return original;
            int lado = Math.max(img.getWidth(), img.getHeight());
            if (original.length <= UMBRAL_BYTES && lado <= MAX_LADO) return original;
            double escala = Math.min(1.0, (double) MAX_LADO / lado);
            int w = Math.max(1, (int) Math.round(img.getWidth() * escala));
            int h = Math.max(1, (int) Math.round(img.getHeight() * escala));
            BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(img, 0, 0, w, h, null);
            } finally {
                g.dispose();
            }
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(CALIDAD);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(salida)) {
                writer.setOutput(out);
                writer.write(null, new IIOImage(rgb, null, null), p);
            } finally {
                writer.dispose();
            }
            byte[] reducida = salida.toByteArray();
            return reducida.length < original.length ? reducida : original;
        } catch (Exception | OutOfMemoryError e) {
            return original;
        }
    }
}
