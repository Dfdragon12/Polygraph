package com.polygraph.erp.modules.trabajos.service;

import com.polygraph.erp.modules.formularios.service.DocumentoPdfConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** PC-08: el servidor dice qué le falta (LibreOffice, carpetas, URLs de desarrollo) y la prueba de PDF genera uno de verdad cuando LibreOffice está. */
@DisplayName("PC-08 — diagnóstico del servidor")
class PC08DiagnosticoServidorTest {

    @Test
    @DisplayName("Avisa de lo que sigue apuntando a un equipo de desarrollo: localhost, ngrok, http sin https y Swagger abierto")
    void advertencias() {
        assertThat(DiagnosticoServidorService.advertencias("https://app.polygraph.co", "https://app.polygraph.co", false)).isEmpty();

        List<String> dev = DiagnosticoServidorService.advertencias("http://localhost:5173", "http://localhost:5173,https://x.ngrok-free.dev", true);
        assertThat(dev).hasSize(3);
        assertThat(dev.get(0)).contains("FRONTEND_URL").contains("localhost");
        assertThat(dev.get(1)).contains("CORS_ALLOWED_ORIGINS");
        assertThat(dev.get(2)).contains("SWAGGER_ENABLED");

        assertThat(DiagnosticoServidorService.advertencias("http://app.polygraph.co", "https://app.polygraph.co", false)).singleElement().asString().contains("https");
        assertThat(DiagnosticoServidorService.advertencias("", "", false)).hasSize(2);   // sin definir tampoco es correcto
        assertThat(DiagnosticoServidorService.advertencias(null, null, false)).hasSize(2);
    }

    @Test
    @DisplayName("Sin LibreOffice o con una ruta equivocada, el diagnóstico lo dice con el motivo y la prueba de PDF no lanza: devuelve ok=false")
    void sinLibreOffice(@TempDir Path carpeta) {
        DocumentoPdfConverter inexistente = new DocumentoPdfConverter();
        org.springframework.test.util.ReflectionTestUtils.setField(inexistente, "binarioLibreOffice", "no-existe-soffice-" + System.nanoTime());
        var servicio = new DiagnosticoServidorService(inexistente, carpeta.toString(), carpeta.resolve("trabajos").toString(), "https://x.co", "https://x.co", false);

        var d = servicio.diagnostico();
        assertThat(d.libreOffice().disponible()).isFalse();
        assertThat(d.libreOffice().error()).isNotBlank();
        assertThat(d.todoBien()).isFalse();
        assertThat(d.carpetas().get(0).existe()).isTrue();
        assertThat(d.carpetas().get(0).escribible()).isTrue();
        assertThat(d.carpetas().get(1).existe()).isFalse();   // la carpeta de trabajos todavía no existe: se ve
        assertThat(d.advertencias()).isEmpty();

        var prueba = servicio.probarPdf();
        assertThat(prueba.ok()).isFalse();
        assertThat(prueba.mensaje()).isNotBlank();
    }

    @Test
    @DisplayName("Localiza el ejecutable por ruta completa o por el PATH, con o sin .exe, y devuelve null si no está")
    void localizaElEjecutable(@TempDir Path carpeta) throws Exception {
        Path falso = java.nio.file.Files.createFile(carpeta.resolve("soffice.exe"));
        falso.toFile().setExecutable(true);
        String sep = java.io.File.pathSeparator;
        assertThat(DiagnosticoServidorService.localizar("soffice", "/no/existe" + sep + carpeta)).isEqualTo(falso);
        assertThat(DiagnosticoServidorService.localizar(carpeta.resolve("soffice").toString(), null)).isEqualTo(falso);
        assertThat(DiagnosticoServidorService.localizar("soffice", "/no/existe")).isNull();
        assertThat(DiagnosticoServidorService.localizar("soffice", null)).isNull();
        assertThat(DiagnosticoServidorService.localizar("", carpeta.toString())).isNull();
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PRUEBA_LIBREOFFICE", matches = "1")
    @DisplayName("[PRUEBA_LIBREOFFICE=1] Con LibreOffice instalado: el diagnóstico lo ve y «Probar PDF» arma un .docx, lo convierte y comprueba que salió un PDF (usa la carpeta temporal configurada)")
    void conLibreOffice(@TempDir Path trabajo) {
        DocumentoPdfConverter real = new DocumentoPdfConverter();
        org.springframework.test.util.ReflectionTestUtils.setField(real, "carpetaTemporalConfigurada", trabajo.toString());
        var servicio = new DiagnosticoServidorService(real, trabajo.toString(), trabajo.toString(), "https://x.co", "https://x.co", false);
        var lo = servicio.diagnostico().libreOffice();
        assumeTrue(lo.disponible(), "LibreOffice no está instalado o no responde en esta máquina — prueba saltada");

        var prueba = servicio.probarPdf();
        assertThat(prueba.ok()).as(prueba.mensaje()).isTrue();
        assertThat(prueba.paginas()).isGreaterThanOrEqualTo(1);
        assertThat(prueba.bytes()).isGreaterThan(500);
        assertThat(trabajo.toFile().list()).as("cada conversión borra su carpeta de trabajo").isEmpty();
    }
}
