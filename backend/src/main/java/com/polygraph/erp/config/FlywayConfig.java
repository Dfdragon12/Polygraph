package com.polygraph.erp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reparación de Flyway de un solo uso, sin plugin de Maven ni CLI.
 *
 *  Flyway guarda un checksum de cada migración aplicada y, si el archivo cambia después, se niega
 *  a arrancar ("Migration checksum mismatch"). Cuando se corrige a propósito una migración vieja
 *  (ej. V152/V154/V156 el 2026-09-28, para que una instalación limpia funcione), cada base ya
 *  existente necesita un "repair": actualiza los checksums guardados a los de los archivos
 *  actuales, SIN volver a ejecutar esas migraciones ni tocar datos.
 *
 *  Uso: arrancar el backend UNA vez con FLYWAY_REPARAR=true (dev y producción), confirmar en el
 *  log "Flyway repair aplicado" y quitar la variable. Con false (el defecto) no cambia nada. */
@Slf4j
@Configuration
public class FlywayConfig {

    @Value("${app.flyway.reparar:false}")
    private boolean reparar;

    @Bean
    public FlywayMigrationStrategy estrategiaMigracion() {
        return flyway -> {
            if (reparar) {
                log.warn("FLYWAY_REPARAR=true — actualizando checksums de migraciones ya aplicadas");
                flyway.repair();
                log.warn("Flyway repair aplicado. Quita FLYWAY_REPARAR del entorno para los siguientes arranques.");
            }
            flyway.migrate();
        };
    }
}
