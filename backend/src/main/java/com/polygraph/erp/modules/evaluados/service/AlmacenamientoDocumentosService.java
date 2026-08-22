package com.polygraph.erp.modules.evaluados.service;

import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class AlmacenamientoDocumentosService {

    private static final Set<String> EXTENSIONES_PERMITIDAS = Set.of("pdf", "jpg", "jpeg", "png");

    private final Path directorioBase;

    public AlmacenamientoDocumentosService(@Value("${app.storage.uploads-dir}") String uploadsDir) {
        this.directorioBase = Path.of(uploadsDir).toAbsolutePath().normalize();
    }

    /**
     * Guarda el archivo bajo {directorioBase}/{categoria}/{carpeta}/{uuid}_{nombreSanitizado} y
     * devuelve la ruta relativa a persistir. "categoria" separa por tipo de dueño del documento
     * (p.ej. "evaluados" vs "clientes") para que nunca compartan carpeta numérica — antes ambos
     * caían en uploads/evaluados/{id} y un candidato y un cliente con el mismo id numérico podían
     * chocar. "carpeta" es la clave dentro de esa categoría (id de servicio, NIT del cliente, etc.),
     * decidida por quien llama.
     */
    public String guardar(MultipartFile archivo, String categoria, String carpeta) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ApiException("El archivo está vacío", HttpStatus.BAD_REQUEST);
        }
        String nombreOriginal = sanitizarNombre(archivo.getOriginalFilename());
        String extension = extraerExtension(nombreOriginal);
        if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
            throw new ApiException(
                    "Tipo de archivo no permitido. Solo se aceptan: " + String.join(", ", EXTENSIONES_PERMITIDAS),
                    HttpStatus.BAD_REQUEST);
        }

        String rutaRelativa = sanitizarCarpeta(categoria) + "/" + sanitizarCarpeta(carpeta) + "/"
                + UUID.randomUUID() + "_" + nombreOriginal;
        Path destino = directorioBase.resolve(rutaRelativa).normalize();
        if (!destino.startsWith(directorioBase)) {
            throw new ApiException("Ruta de archivo inválida", HttpStatus.BAD_REQUEST);
        }

        try {
            Files.createDirectories(destino.getParent());
            Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Error guardando documento en {}: {}", destino, e.getMessage(), e);
            throw new ApiException("No se pudo guardar el archivo", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return rutaRelativa;
    }

    public Resource cargarComoResource(String rutaRelativa) {
        Path ruta = directorioBase.resolve(rutaRelativa).normalize();
        if (!ruta.startsWith(directorioBase) || !Files.exists(ruta)) {
            throw new ApiException("Archivo no encontrado", HttpStatus.NOT_FOUND);
        }
        return new FileSystemResource(ruta);
    }

    public void eliminar(String rutaRelativa) {
        if (rutaRelativa == null || rutaRelativa.isBlank()) return;
        try {
            Path ruta = directorioBase.resolve(rutaRelativa).normalize();
            if (ruta.startsWith(directorioBase)) {
                Files.deleteIfExists(ruta);
            }
        } catch (IOException e) {
            log.warn("No se pudo eliminar el archivo {}: {}", rutaRelativa, e.getMessage());
        }
    }

    /** Igual de estricto que sanitizarNombre, pero sin puntos — es un segmento de carpeta, no un nombre de archivo. */
    private String sanitizarCarpeta(String valor) {
        String base = (valor == null || valor.isBlank()) ? "sin-clasificar" : valor.trim();
        base = base.replaceAll("[^a-zA-Z0-9-]", "_");
        return base.length() > 80 ? base.substring(0, 80) : base;
    }

    private String sanitizarNombre(String nombre) {
        String base = (nombre == null || nombre.isBlank()) ? "documento" : nombre;
        base = base.replaceAll("[\\\\/]", "_").replaceAll("[^a-zA-Z0-9._-]", "_");
        return base.length() > 150 ? base.substring(base.length() - 150) : base;
    }

    private String extraerExtension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto >= 0 && punto < nombre.length() - 1 ? nombre.substring(punto + 1).toLowerCase() : "";
    }

    public String tipoContenido(String nombreArchivo) {
        return switch (extraerExtension(nombreArchivo)) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> "application/octet-stream";
        };
    }
}
