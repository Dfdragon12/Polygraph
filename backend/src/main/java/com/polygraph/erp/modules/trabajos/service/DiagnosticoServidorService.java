package com.polygraph.erp.modules.trabajos.service;

import com.polygraph.erp.modules.formularios.service.DocumentoPdfConverter;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * PC-08 · Una sola respuesta para quien despliega: si LibreOffice está instalado y responde, si las carpetas existen, se pueden escribir y tienen espacio, y avisos de lo que
 * sigue apuntando a un equipo de desarrollo (URLs en localhost, Swagger abierto). Además «Probar PDF» arma de verdad un .docx de prueba, lo convierte con LibreOffice y comprueba
 * que salió un PDF: es lo mismo que hacen el PDF de un subproceso y el del estudio completo. Solo lo ve el administrador. Al arrancar deja el resultado en el registro, para que un
 * servidor sin LibreOffice no se entere recién cuando un cliente pide su PDF.
 */
@Slf4j
@Service
public class DiagnosticoServidorService {

    public record Carpeta(String nombre, String ruta, boolean existe, boolean escribible, long espacioLibreMb) {}
    public record LibreOffice(String binario, boolean disponible, String version, String error) {}
    public record Diagnostico(LibreOffice libreOffice, List<Carpeta> carpetas, List<String> advertencias, boolean todoBien) {}
    public record PruebaPdf(boolean ok, long milisegundos, int paginas, int bytes, String mensaje) {}

    private final DocumentoPdfConverter convertidor;
    private final String carpetaDocumentos;
    private final String carpetaTrabajos;
    private final String urlFrontend;
    private final String origenesCors;
    private final boolean swaggerAbierto;

    public DiagnosticoServidorService(DocumentoPdfConverter convertidor,
                                      @Value("${app.storage.uploads-dir:./uploads}") String carpetaDocumentos,
                                      @Value("${app.trabajos.directorio:${app.storage.uploads-dir:./uploads}/trabajos}") String carpetaTrabajos,
                                      @Value("${app.frontend.url:}") String urlFrontend,
                                      @Value("${app.cors.allowed-origins:}") String origenesCors,
                                      @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerAbierto) {
        this.convertidor = convertidor;
        this.carpetaDocumentos = carpetaDocumentos;
        this.carpetaTrabajos = carpetaTrabajos;
        this.urlFrontend = urlFrontend;
        this.origenesCors = origenesCors;
        this.swaggerAbierto = swaggerAbierto;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        // En un hilo aparte: «soffice --version» puede tardar la primera vez y la aplicación ya está lista.
        Thread t = new Thread(() -> {
            try {
                Diagnostico d = diagnostico();
                if (d.libreOffice().disponible()) log.info("LibreOffice disponible ({}): {}", d.libreOffice().binario(), d.libreOffice().version());
                else log.error("LibreOffice NO responde ({}): {}. Sin él no se generan los PDF (informes, formularios ni estudio completo). Instálalo o ajusta LIBREOFFICE_BINARIO.",
                        d.libreOffice().binario(), d.libreOffice().error());
                d.carpetas().stream().filter(c -> !c.existe() || !c.escribible()).forEach(c ->
                        log.error("La carpeta de {} ({}) {}", c.nombre(), c.ruta(), !c.existe() ? "no existe" : "no se puede escribir"));
                d.advertencias().forEach(a -> log.warn("Configuración: {}", a));
            } catch (RuntimeException e) {
                log.warn("No se pudo revisar el servidor al arrancar: {}", e.getMessage());
            }
        }, "diagnostico-servidor");
        t.setDaemon(true);
        t.start();
    }

    public Diagnostico diagnostico() {
        LibreOffice lo = libreOffice();
        List<Carpeta> carpetas = new ArrayList<>();
        carpetas.add(carpeta("Documentos y plantillas Word (UPLOADS_DIR)", carpetaDocumentos));
        carpetas.add(carpeta("Trabajos en segundo plano (TRABAJOS_DIR)", carpetaTrabajos));
        String temporal = convertidor.carpetaTemporalConfigurada();
        carpetas.add(carpeta("Trabajo de las conversiones a PDF (LIBREOFFICE_TEMP_DIR)", temporal.isEmpty() ? System.getProperty("java.io.tmpdir") : temporal));
        List<String> advertencias = advertencias(urlFrontend, origenesCors, swaggerAbierto);
        boolean bien = lo.disponible() && carpetas.stream().allMatch(c -> c.existe() && c.escribible()) && advertencias.isEmpty();
        return new Diagnostico(lo, carpetas, advertencias, bien);
    }

    /** Lo que sigue apuntando a un equipo de desarrollo (puro: se prueba sin servidor). */
    static List<String> advertencias(String urlFrontend, String origenesCors, boolean swaggerAbierto) {
        List<String> a = new ArrayList<>();
        if (urlFrontend == null || urlFrontend.isBlank() || urlFrontend.contains("localhost") || urlFrontend.contains("127.0.0.1"))
            a.add("FRONTEND_URL apunta a localhost: los enlaces de los correos (restablecer contraseña, enlace del candidato) llegarían a un lugar que no existe. Pon el dominio público con https.");
        else if (!urlFrontend.startsWith("https://"))
            a.add("FRONTEND_URL no usa https: en producción todo debe ir por HTTPS.");
        if (origenesCors == null || origenesCors.isBlank() || origenesCors.contains("localhost") || origenesCors.contains("ngrok"))
            a.add("CORS_ALLOWED_ORIGINS incluye localhost o ngrok (o está vacío): déjalo solo con el dominio público.");
        if (swaggerAbierto) a.add("SWAGGER_ENABLED está activo: la documentación de la API queda visible sin iniciar sesión. Apágalo en producción.");
        return a;
    }

    /**
     * ¿Está el ejecutable? Se busca en disco (la ruta completa o el PATH) sin arrancarlo: en Windows «soffice --version» tarda 15-30 s y no imprime nada. Que de verdad convierta lo dice
     * «Probar PDF».
     */
    private LibreOffice libreOffice() {
        String binario = convertidor.binario();
        Path ruta = localizar(binario, System.getenv("PATH"));
        if (ruta == null) return new LibreOffice(binario, false, null, "no se encontró «" + binario + "» (ni como ruta ni en el PATH). Instala LibreOffice o ajusta LIBREOFFICE_BINARIO.");
        return new LibreOffice(binario, true, "instalado en " + ruta, null);
    }

    static Path localizar(String binario, String path) {
        if (binario == null || binario.isBlank()) return null;
        String[] extensiones = {"", ".exe", ".com", ".bat", ".cmd"};
        if (binario.contains("/") || binario.contains("\\")) {
            for (String e : extensiones) { Path p = Path.of(binario + e); if (Files.isRegularFile(p) && Files.isExecutable(p)) return p; }
            return null;
        }
        if (path == null) return null;
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) continue;
            for (String e : extensiones) {
                try { Path p = Path.of(dir.trim()).resolve(binario + e); if (Files.isRegularFile(p) && Files.isExecutable(p)) return p; } catch (RuntimeException ignorada) { /* entrada del PATH inválida */ }
            }
        }
        return null;
    }

    private static Carpeta carpeta(String nombre, String ruta) {
        Path p = Path.of(ruta).toAbsolutePath().normalize();
        boolean existe = Files.isDirectory(p);
        boolean escribible = existe && Files.isWritable(p);
        long libre = -1;
        try { if (existe) libre = Files.getFileStore(p).getUsableSpace() / (1024 * 1024); } catch (IOException ignorado) { /* sin dato */ }
        return new Carpeta(nombre, p.toString(), existe, escribible, libre);
    }

    /** Arma un .docx de prueba, lo convierte con LibreOffice (como cualquier PDF del sistema) y comprueba que salió un PDF de verdad. Nunca lanza: devuelve ok=false con el motivo. */
    public PruebaPdf probarPdf() {
        long inicio = System.currentTimeMillis();
        try (XWPFDocument docx = new XWPFDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            docx.createParagraph().createRun().setText("Polygraph Service · prueba de generación de PDF");
            docx.createParagraph().createRun().setText("Generado el " + LocalDateTime.now() + ". Si lees esto en un PDF, LibreOffice funciona en este servidor.");
            docx.write(salida);
            byte[] pdf = convertidor.convertir(salida.toByteArray());
            if (pdf.length < 5 || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F') return new PruebaPdf(false, System.currentTimeMillis() - inicio, 0, pdf.length, "LibreOffice respondió, pero el archivo no es un PDF.");
            try (PDDocument d = Loader.loadPDF(pdf)) {
                long ms = System.currentTimeMillis() - inicio;
                log.info("Prueba de PDF: correcta en {} ms ({} página(s), {} bytes)", ms, d.getNumberOfPages(), pdf.length);
                return new PruebaPdf(true, ms, d.getNumberOfPages(), pdf.length, "El PDF se generó bien.");
            }
        } catch (Exception e) {
            log.warn("Prueba de PDF: falló: {}", e.getMessage());
            return new PruebaPdf(false, System.currentTimeMillis() - inicio, 0, 0, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
