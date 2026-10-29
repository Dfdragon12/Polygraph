package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.service.CalendarioHabilService;
import com.polygraph.erp.shared.service.DiasHabilesService;
import com.polygraph.erp.shared.service.FestivoService;
import com.polygraph.erp.shared.service.ParametrosTiempoService;
import com.polygraph.erp.shared.service.TiempoServicioService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ACEPTACIÓN en el ambiente de pruebas (Fase 7): corre el backfill REAL contra una COPIA del esquema de la base de desarrollo, con Hibernate validando el
 * esquema (ddl-auto=validate) y el reloj fijado en el lunes 19-oct-2026, y revisa los casos reales #1, #12, #21, #30, #31 y #32.
 *
 * No corre con la suite normal: solo si existe la variable de entorno F7_ESQUEMA (nombre del esquema copiado, ej. prueba_f7; "public" se rechaza).
 * F7_SALIDA es la carpeta donde deja la tabla y el reporte CSV. OJO: al arrancar el contexto corre el backfill de arranque (TiempoHistoricoInicializador),
 * que SÍ confirma sus cambios, pero solo en el esquema copiado.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
// Nunca sobre "public": la configuración base fija hibernate.default_schema=public, así que aquí se pisa con el esquema copiado.
@EnabledIfEnvironmentVariable(named = "F7_ESQUEMA", matches = "(?!public$)(?!PUBLIC$).+")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/${DB_NAME}?currentSchema=${F7_ESQUEMA}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=${DB_USER}",
        "spring.datasource.password=${DB_PASS}",
        "spring.jpa.properties.hibernate.default_schema=${F7_ESQUEMA}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "app.storage.uploads-dir=${F7_SALIDA:./target/f7}"})
@Import({TiempoHistoricoInicializador.class, SemaforoTiempoService.class, NotificacionesTiempoService.class, CalendarioHabilService.class,
        FestivoService.class, ParametrosTiempoService.class, DiasHabilesService.class, TiempoServicioService.class,
        AceptacionTiempoAmbienteTest.RelojFijo.class})
@DisplayName("Aceptación en el ambiente de pruebas — lunes 19-oct-2026")
class AceptacionTiempoAmbienteTest {

    /** Hoy = lunes 19-oct-2026, 10:00 en Bogotá. */
    @TestConfiguration
    static class RelojFijo {
        @Bean
        @Primary
        Clock reloj() {
            return Clock.fixed(Instant.parse("2026-10-19T15:00:00Z"), ZoneId.of("UTC"));
        }
    }

    private static final LocalDate HOY = LocalDate.of(2026, 10, 19);
    private static final List<Integer> CASOS = List.of(1, 12, 21, 30, 31, 32);

    @MockBean private NotificacionService notificaciones;
    @Autowired private TiempoHistoricoInicializador inicializador;
    @Autowired private TiempoServicioService tiempoServicioService;
    @Autowired private ServicioRepository servicioRepository;
    @Autowired private EntityManager em;

    /** Lo que mostraba la plataforma ANTES, con la regla vieja: días de retraso en días CALENDARIO desde la fecha de entrega (solo las abiertas). */
    private static String antes(Servicio s) {
        boolean abierta = s.getEstado() != com.polygraph.erp.shared.enums.EstadoServicio.PUBLICADO && s.getEstado() != com.polygraph.erp.shared.enums.EstadoServicio.CANCELADO;
        if (!abierta) return s.getEstado() == com.polygraph.erp.shared.enums.EstadoServicio.PUBLICADO ? "«Entregada» (sin decir si fue tarde)" : "—";
        long dias = ChronoUnit.DAYS.between(s.getFechaEntregaEstimada(), HOY);
        return dias > 0 ? dias + (dias == 1 ? " día de retraso" : " días de retraso") + " (calendario)" : dias == 0 ? "Se entrega hoy" : "Faltan " + (-dias) + " días (calendario)";
    }

    @Test
    @DisplayName("Backfill sobre la copia + casos de aceptación")
    void aceptacion() throws IOException {
        Map<Integer, String> mostrabaAntes = new java.util.LinkedHashMap<>();
        Map<Integer, String> estadoAntes = new java.util.LinkedHashMap<>();
        for (Integer id : CASOS) {
            Servicio s = servicioRepository.findById(id).orElseThrow();
            mostrabaAntes.put(id, antes(s));
            estadoAntes.put(id, s.getEstado() + (s.getEstadoTiempo() == null ? "" : " / " + s.getEstadoTiempo()));
        }
        // Seguro: la prueba solo trabaja en el esquema copiado
        assertThat(em.createNativeQuery("select current_schema()").getSingleResult()).isEqualTo(System.getenv("F7_ESQUEMA"));
        // El backfill ya corrió al arrancar el contexto (igual que al arrancar la aplicación): su reporte quedó en {F7_SALIDA}/reportes
        Path carpetaReportes = Path.of(System.getenv().getOrDefault("F7_SALIDA", "target/f7"), "reportes");
        Path ultimo;
        try (var archivos = Files.list(carpetaReportes)) {
            ultimo = archivos.filter(f -> f.getFileName().toString().startsWith("tiempo-historico-")).max(java.util.Comparator.comparing(f -> f.toFile().lastModified())).orElseThrow();
        }
        List<String> reporte = Files.readAllLines(ultimo, StandardCharsets.UTF_8);
        long sinCompletar = servicioRepository.findByFechaRecepcionEfectivaIsNull().size();
        em.clear();

        StringBuilder salida = new StringBuilder();
        salida.append("Solicitudes sin datos de tiempo antes del backfill: ").append(sinCompletar).append("\n");
        salida.append("Filas del reporte (sin encabezado): ").append(Math.max(0, reporte.size() - 1)).append("\n\n");
        salida.append("| # | Evaluado | Etapa | Recepción efectiva | Plazo | Compromiso | Pausa (días hábiles) | Estado de tiempo | Chip | Retraso/Restan | Entrega real | Antes |\n|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        List<String> fallos = new ArrayList<>();
        for (Integer id : CASOS) {
            Servicio s = servicioRepository.findById(id).orElseThrow();
            var t = tiempoServicioService.de(s);
            salida.append("| ").append(id).append(" | ").append(s.getCandidato() == null ? "" : (s.getCandidato().getNombres() + " " + s.getCandidato().getApellidos()).trim())
                    .append(" | ").append(s.getEstado()).append(" | ").append(s.getFechaRecepcionEfectiva()).append(" | ").append(s.getPlazoDiasHabiles())
                    .append(" | ").append(s.getFechaEntregaEstimada()).append(" | ").append(s.getDiasPausaHabiles()).append(" | ").append(s.getEstadoTiempo())
                    .append(" | ").append(t == null ? "—" : t.etiqueta()).append(" | ").append(s.getDiasRetrasoHabiles()).append("/").append(s.getDiasRestantesHabiles())
                    .append(" | ").append(s.getFechaEntregaReal()).append(" | ").append(mostrabaAntes.get(id)).append(" |\n");
        }
        salida.append("\nCálculo paso a paso\n");
        for (Integer id : CASOS) {
            Servicio s = servicioRepository.findById(id).orElseThrow();
            salida.append("\n#").append(id).append(": solicitada ").append(s.getFechaSolicitud()).append(" ").append(s.getHoraSolicitud()).append(" → recepción efectiva ")
                    .append(s.getFechaRecepcionEfectiva()).append("; plazo ").append(s.getPlazoDiasHabiles()).append(" días hábiles → compromiso original ")
                    .append(s.getFechaCompromisoOriginal()).append("; pausas ").append(s.getDiasPausaHabiles()).append(" días hábiles → compromiso ajustado ")
                    .append(s.getFechaCompromisoAjustada()).append("; hoy ").append(HOY).append(" → ").append(s.getEstadoTiempo()).append(" (retraso ")
                    .append(s.getDiasRetrasoHabiles()).append(", restan ").append(s.getDiasRestantesHabiles()).append(")");
            if (s.getFechaEntregaReal() != null) salida.append("; entregada ").append(s.getFechaEntregaReal()).append(" → ").append(s.getCumplimiento());
        }

        // ── casos de aceptación (si alguno no coincide NO se ajusta la regla: se muestra el cálculo de arriba)
        verificar(fallos, 21, EstadoTiempo.VENCIDA, 17, "Vencida · 17 días hábiles");
        verificar(fallos, 31, EstadoTiempo.VENCIDA, 1, "Vencida · 1 día hábil");
        verificar(fallos, 32, EstadoTiempo.POR_VENCER, 0, "Vence hoy");
        verificar(fallos, 30, EstadoTiempo.POR_VENCER, 0, "Vence mañana");
        verificar(fallos, 12, EstadoTiempo.ENTREGADA_TARDE, null, null);
        verificar(fallos, 1, EstadoTiempo.EN_PAUSA, 0, "En pausa · esperando al candidato");
        salida.append("\n\nResultado de la aceptación: ").append(fallos.isEmpty() ? "TODOS los casos coinciden" : "NO coinciden: " + fallos).append("\n");

        String carpeta = System.getenv().getOrDefault("F7_SALIDA", "target/f7");
        Files.createDirectories(Path.of(carpeta));
        Files.writeString(Path.of(carpeta, "aceptacion-fase7.md"), salida.toString(), StandardCharsets.UTF_8);
        Files.write(Path.of(carpeta, "backfill-reporte.csv"), reporte, StandardCharsets.UTF_8);
        System.out.println(salida);

        assertThat(fallos).as("casos de aceptación que no coinciden (ver aceptacion-fase7.md)").isEmpty();
    }

    private void verificar(List<String> fallos, int id, EstadoTiempo esperado, Integer retraso, String etiqueta) {
        Servicio s = servicioRepository.findById(id).orElseThrow();
        var t = tiempoServicioService.de(s);
        if (s.getEstadoTiempo() != esperado) fallos.add("#" + id + " estado " + s.getEstadoTiempo() + " ≠ " + esperado);
        if (esperado == EstadoTiempo.VENCIDA && (s.getDiasRetrasoHabiles() == null || !s.getDiasRetrasoHabiles().equals(retraso))) fallos.add("#" + id + " retraso " + s.getDiasRetrasoHabiles() + " ≠ " + retraso);
        if (etiqueta != null && (t == null || !etiqueta.equals(t.etiqueta()))) fallos.add("#" + id + " chip «" + (t == null ? null : t.etiqueta()) + "» ≠ «" + etiqueta + "»");
    }
}
