package com.polygraph.erp.modules.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Client ID de la app OAuth de Google (Google Cloud Console) usada para "Continuar con Google"
 *  en registro/login. Vacío por defecto — sin configurar, ese botón queda deshabilitado. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "google.oauth")
public class GoogleOAuthProperties {
    private String clientId;
}
