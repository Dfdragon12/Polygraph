package com.polygraph.erp.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Flyway reemplaza todo lo que tenga la forma de signo de pesos + llave en un .sql (también dentro
 *  de los comentarios) y, si no conoce esa variable, el backend no arranca ("No value provided for
 *  placeholder"). Pasó con V204 (2026-10-05): un comentario citaba un marcador del Word. En pruebas
 *  Flyway está apagado (H2), así que este es el único aviso antes de arrancar. */
@DisplayName("Migraciones: ningún .sql trae marcadores que Flyway confunda con variables")
class MigracionesSinMarcadoresFlywayTest {

    @Test
    void sinMarcadores() throws Exception {
        try (Stream<Path> archivos = Files.list(Path.of("src/main/resources/db/migration"))) {
            List<String> conMarcador = archivos.filter(p -> p.toString().endsWith(".sql"))
                    .filter(p -> {
                        try {
                            return Files.readString(p, StandardCharsets.UTF_8).contains("${");
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(p -> p.getFileName().toString())
                    .toList();
            assertThat(conMarcador).as("migraciones con signo de pesos + llave").isEmpty();
        }
    }
}
