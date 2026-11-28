package com.polygraph.erp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** El reloj del sistema, inyectable para que el cálculo de tiempos se pueda probar con una fecha fija. La zona horaria de los
 *  cálculos no sale de aquí sino del parámetro zona_horaria (America/Bogota). */
@Configuration
public class RelojConfig {

    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}
