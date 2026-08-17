package com.polygraph.erp.modules.pagos.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Cliente HTTP para llamar a la API de Wompi (consultas directas, p. ej. ConciliacionPagosJob). */
@Slf4j
@Configuration
public class WompiRestClientConfig {

    private static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(5);
    private static final Duration TIMEOUT_LECTURA = Duration.ofSeconds(10);

    @Bean
    public RestClient wompiRestClient(WompiProperties wompiProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_CONEXION);
        requestFactory.setReadTimeout(TIMEOUT_LECTURA);

        String baseUrl = wompiProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("WOMPI_BASE_URL no está configurado — el cliente HTTP de Wompi quedará sin base-url");
            baseUrl = "";
        }

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
