package com.polygraph.erp.modules.evaluados.service;

import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;

/** Los documentos que sube el candidato desde su link público: solo PDF o imagen (JPG/PNG), con
 *  tamaño máximo (app.candidato.documentos.*). Se revisa la FIRMA del archivo (primeros bytes),
 *  no solo la extensión: un .exe renombrado a .pdf no pasa. Es un endpoint sin login, así que
 *  el filtro va aquí y no en AlmacenamientoDocumentosService (que también usan otros módulos). */
@Slf4j
@Component
public class ValidadorArchivoCandidato {

    enum Tipo { PDF, IMAGEN }

    private static final byte[] FIRMA_PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] FIRMA_JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final long maxPdfBytes;
    private final long maxImagenBytes;

    public ValidadorArchivoCandidato(@Value("${app.candidato.documentos.max-pdf-mb:5}") int maxPdfMb,
                                     @Value("${app.candidato.documentos.max-imagen-mb:5}") int maxImagenMb) {
        this.maxPdfBytes = maxPdfMb * 1024L * 1024L;
        this.maxImagenBytes = maxImagenMb * 1024L * 1024L;
    }

    public void validar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ApiException("El archivo está vacío.", HttpStatus.BAD_REQUEST);
        }
        String nombre = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        Tipo porExtension = nombre.endsWith(".pdf") ? Tipo.PDF
                : (nombre.endsWith(".jpg") || nombre.endsWith(".jpeg") || nombre.endsWith(".png")) ? Tipo.IMAGEN
                : null;
        if (porExtension == null) {
            throw new ApiException("Solo se aceptan archivos PDF o imágenes JPG/PNG.", HttpStatus.BAD_REQUEST);
        }

        Tipo real = tipoPorContenido(archivo);
        if (real != porExtension) {
            log.warn("Archivo de candidato rechazado: '{}' no es realmente {}", archivo.getOriginalFilename(), porExtension);
            throw new ApiException("El archivo no es un PDF o una imagen válida. Revisa que no esté dañado o renombrado.",
                    HttpStatus.BAD_REQUEST);
        }

        long max = real == Tipo.PDF ? maxPdfBytes : maxImagenBytes;
        if (archivo.getSize() > max) {
            throw new ApiException(String.format("%s pesa %s y el máximo es %d MB. %s",
                    real == Tipo.PDF ? "El PDF" : "La imagen", mb(archivo.getSize()), max / (1024 * 1024),
                    real == Tipo.PDF ? "Escanéalo a menor resolución o en blanco y negro."
                            : "Tómala con menor resolución o envíala comprimida."),
                    HttpStatus.BAD_REQUEST);
        }
    }

    public long maxPdfMb() { return maxPdfBytes / (1024 * 1024); }

    public long maxImagenMb() { return maxImagenBytes / (1024 * 1024); }

    private static Tipo tipoPorContenido(MultipartFile archivo) {
        byte[] cabecera = new byte[8];
        int leidos;
        try (InputStream in = archivo.getInputStream()) {
            leidos = in.readNBytes(cabecera, 0, cabecera.length);
        } catch (IOException e) {
            throw new ApiException("No se pudo leer el archivo.", HttpStatus.BAD_REQUEST);
        }
        if (empiezaCon(cabecera, leidos, FIRMA_PDF)) return Tipo.PDF;
        if (empiezaCon(cabecera, leidos, FIRMA_JPG) || empiezaCon(cabecera, leidos, FIRMA_PNG)) return Tipo.IMAGEN;
        return null;
    }

    private static boolean empiezaCon(byte[] datos, int leidos, byte[] firma) {
        return leidos >= firma.length && Arrays.equals(datos, 0, firma.length, firma, 0, firma.length);
    }

    private static String mb(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
