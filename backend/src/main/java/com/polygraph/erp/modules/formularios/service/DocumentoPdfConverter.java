package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Convierte a PDF el .docx que ya arma FormularioPlantillaWordService, usando LibreOffice en
 *  modo headless (el binario "soffice") — el mismo motor de renderizado que usa LibreOffice/
 *  OpenOffice, compatible con el formato de Word de verdad. Se eligió esto en vez de un motor
 *  Java puro (docx4j + Apache FOP, la primera versión de esta clase) porque se confirmó con
 *  evidencia real que FOP sustituye fuentes por genéricas (Helvetica) y dibuja las tablas de
 *  forma distinta a como se ven en Word — el resultado no se parecía al documento original.
 *  LibreOffice headless si abre y renderiza el .docx tal como lo haría Word.
 *
 *  Requiere tener LibreOffice instalado donde corra el backend — ver `app.libreoffice.binario`
 *  en application.yml. Local (Windows, sin instalador en el PATH): configurar esa propiedad con
 *  la ruta completa a soffice.exe. En producción (backend/Dockerfile): ya viene instalado dentro
 *  de la imagen, con "soffice" en el PATH — no hace falta tocar código ni configuración al
 *  desplegar, solo construir la imagen con ese Dockerfile. */
@Slf4j
@Service
public class DocumentoPdfConverter {

    // Con valor literal además de @Value a propósito: en pruebas esta clase se instancia con
    // "new" directo, sin contenedor de Spring que resuelva @Value — así igual queda usable.
    @Value("${app.libreoffice.binario:soffice}")
    private String binarioLibreOffice = "soffice";

    @Value("${app.libreoffice.timeout-segundos:60}")
    private long timeoutSegundos = 60;

    public byte[] convertir(byte[] docx) {
        Path carpetaTemporal = null;
        try {
            carpetaTemporal = Files.createTempDirectory("polygraph-pdf-");
            Path entrada = carpetaTemporal.resolve("documento.docx");
            Files.write(entrada, docx);

            // -env:UserInstallation con un perfil único por conversión — sin esto, dos
            // conversiones al mismo tiempo compiten por el mismo perfil de usuario de LibreOffice
            // y una de las dos falla con "another instance is already running". Con un perfil
            // aparte por invocación, varias conversiones simultáneas son seguras.
            Path perfilTemporal = carpetaTemporal.resolve("perfil-lo-" + UUID.randomUUID());

            ProcessBuilder pb = new ProcessBuilder(
                    binarioLibreOffice,
                    "--headless", "--norestore", "--nologo", "--nofirststartwizard",
                    "-env:UserInstallation=" + perfilTemporal.toUri(),
                    "--convert-to", "pdf",
                    "--outdir", carpetaTemporal.toString(),
                    entrada.toString());
            pb.redirectErrorStream(true);

            Process proceso = pb.start();
            String salidaProceso;
            try (var in = proceso.getInputStream()) {
                salidaProceso = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            boolean terminoATiempo = proceso.waitFor(timeoutSegundos, TimeUnit.SECONDS);
            if (!terminoATiempo) {
                proceso.destroyForcibly();
                log.error("LibreOffice no terminó la conversión en {}s — salida hasta ese momento: {}", timeoutSegundos, salidaProceso);
                throw new ApiException("La conversión a PDF tardó demasiado.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            if (proceso.exitValue() != 0) {
                log.error("LibreOffice terminó con código {}: {}", proceso.exitValue(), salidaProceso);
                throw new ApiException(
                        "No se pudo convertir el documento a PDF — revisa que LibreOffice esté instalado y bien configurado (app.libreoffice.binario).",
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }

            Path salida = carpetaTemporal.resolve("documento.pdf");
            if (!Files.exists(salida)) {
                log.error("LibreOffice terminó sin error pero no generó el PDF esperado. Salida del proceso: {}", salidaProceso);
                throw new ApiException("La conversión no generó ningún PDF.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return Files.readAllBytes(salida);
        } catch (ApiException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("No se pudo convertir el documento generado a PDF: {}", e.getMessage(), e);
            throw new ApiException(
                    "No se pudo generar el PDF — revisa que LibreOffice esté instalado en el servidor (ver app.libreoffice.binario).",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (carpetaTemporal != null) borrarRecursivo(carpetaTemporal);
        }
    }

    private void borrarRecursivo(Path carpeta) {
        try (var flujo = Files.walk(carpeta)) {
            List<Path> rutas = flujo.sorted(Comparator.reverseOrder()).toList();
            for (Path ruta : rutas) {
                try {
                    Files.deleteIfExists(ruta);
                } catch (IOException e) {
                    log.warn("No se pudo borrar {} de la carpeta temporal de conversión: {}", ruta, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("No se pudo limpiar la carpeta temporal {}: {}", carpeta, e.getMessage());
        }
    }
}
