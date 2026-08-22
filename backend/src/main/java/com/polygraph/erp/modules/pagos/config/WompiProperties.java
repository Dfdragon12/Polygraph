package com.polygraph.erp.modules.pagos.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades de configuración de la pasarela Wompi.
 * No se define {@code toString()} a propósito: privateKey, integritySecret y eventsSecret
 * nunca deben quedar expuestos en logs.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "wompi")
public class WompiProperties {

    private String publicKey;
    private String privateKey;
    private String integritySecret;
    private String eventsSecret;
    private String baseUrl;
    private String redirectUrl;
}
