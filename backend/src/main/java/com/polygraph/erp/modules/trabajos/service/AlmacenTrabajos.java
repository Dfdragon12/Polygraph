package com.polygraph.erp.modules.trabajos.service;

import com.polygraph.erp.modules.trabajos.config.PropiedadesTrabajos;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/** Archivos de la cola en disco — la BD solo guarda rutas RELATIVAS a app.trabajos.directorio:
 *
 *    entradas/{uuid}.xlsx     lo que subió el usuario (se borra al completar o al limpiar)
 *    resultados/{uuid}.ext    salida propia de un trabajo (se borra con la fila)
 *    cache/informes/*.pdf     PDFs de informes publicados, compartidos entre trabajos
 *
 *  Nada de esto pasa por la RAM entera: MultipartFile.transferTo copia en streaming, y los
 *  resultados se escriben a un .tmp y se mueven atómicamente (quien lee nunca ve un PDF a medias). */
@Slf4j
@Service
public class AlmacenTrabajos {

    public static final String ENTRADAS = "entradas";
    public static final String RESULTADOS = "resultados";
    public static final String CACHE_INFORMES = "cache/informes";

    private final Path base;

    public AlmacenTrabajos(PropiedadesTrabajos propiedades) {
        this.base = Paths.get(propiedades.getDirectorio()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(base.resolve(ENTRADAS));
            Files.createDirectories(base.resolve(RESULTADOS));
            Files.createDirectories(base.resolve(CACHE_INFORMES));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el directorio de trabajos " + base, e);
        }
    }

    public String guardarEntrada(UUID uuid, MultipartFile archivo, String extension) {
        String relativa = ENTRADAS + "/" + uuid + extension;
        try {
            archivo.transferTo(resolver(relativa));
            return relativa;
        } catch (IOException e) {
            log.error("No se pudo guardar la entrada del trabajo {}: {}", uuid, e.getMessage(), e);
            throw new ApiException("No se pudo recibir el archivo. Intenta de nuevo.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** Escribe a {relativa}.tmp y lo mueve encima de {relativa} en un solo paso. */
    public void escribirAtomico(String relativa, byte[] contenido) throws IOException {
        Path destino = resolver(relativa);
        Files.createDirectories(destino.getParent());
        Path temporal = destino.resolveSibling(destino.getFileName() + "." + UUID.randomUUID() + ".tmp");
        Files.write(temporal, contenido);
        try {
            Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public boolean existe(String relativa) {
        return relativa != null && Files.isRegularFile(resolver(relativa));
    }

    /** Marca el archivo como usado (la limpieza de caché borra por fecha de último uso). */
    public void tocar(String relativa) {
        try {
            Files.setLastModifiedTime(resolver(relativa), FileTime.from(Instant.now()));
        } catch (IOException e) {
            log.debug("No se pudo actualizar la fecha de {}: {}", relativa, e.getMessage());
        }
    }

    public void borrar(String relativa) {
        if (relativa == null) return;
        try {
            Files.deleteIfExists(resolver(relativa));
        } catch (IOException e) {
            log.warn("No se pudo borrar {}: {}", relativa, e.getMessage());
        }
    }

    /** Borra archivos de una carpeta sin usar desde antes de {limite}. Devuelve cuántos borró. */
    public int borrarViejos(String carpeta, Instant limite) {
        int borrados = 0;
        try (Stream<Path> archivos = Files.list(resolver(carpeta))) {
            for (Path archivo : (Iterable<Path>) archivos::iterator) {
                try {
                    if (Files.isRegularFile(archivo) && Files.getLastModifiedTime(archivo).toInstant().isBefore(limite)) {
                        Files.deleteIfExists(archivo);
                        borrados++;
                    }
                } catch (IOException e) {
                    log.warn("No se pudo revisar/borrar {}: {}", archivo, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("No se pudo listar {}: {}", carpeta, e.getMessage());
        }
        return borrados;
    }

    /** Ruta absoluta de una relativa, sin dejar salir de la carpeta base (../ no escapa). */
    public Path resolver(String relativa) {
        Path ruta = base.resolve(relativa).normalize();
        if (!ruta.startsWith(base)) {
            throw new ApiException("Ruta de archivo inválida.", HttpStatus.BAD_REQUEST);
        }
        return ruta;
    }

    public static boolean esResultadoPropio(String relativa) {
        return relativa != null && relativa.startsWith(RESULTADOS + "/");
    }

    /** PDF de un subproceso PUBLICADO, compartido por el informe del cliente (INFORME_PDF) y la
     *  descarga de roles internos (FORMULARIO_PDF): es el mismo documento. fechaPublicado entra
     *  en el nombre, así una re-publicación genera uno nuevo y el viejo lo borra la limpieza. */
    public static String rutaCacheInforme(Long idServicioSubproceso, LocalDateTime fechaPublicado) {
        long publicacion = fechaPublicado != null ? fechaPublicado.toEpochSecond(ZoneOffset.UTC) : 0;
        return CACHE_INFORMES + "/" + idServicioSubproceso + "-" + publicacion + ".pdf";
    }

    public static String rutaResultado(UUID uuid, String extension) {
        return RESULTADOS + "/" + uuid + extension;
    }

    // Locks por franjas (32 fijos): memoria acotada, sin un Map que crezca con cada archivo. Si
    // varias personas piden el mismo PDF a la vez, uno lo genera y los demás lo encuentran hecho.
    private final ReentrantLock[] bloqueos = crearBloqueos(32);

    public ReentrantLock bloqueo(String relativa) {
        return bloqueos[Math.floorMod(relativa.hashCode(), bloqueos.length)];
    }

    private static ReentrantLock[] crearBloqueos(int n) {
        ReentrantLock[] l = new ReentrantLock[n];
        for (int i = 0; i < n; i++) l[i] = new ReentrantLock();
        return l;
    }
}
