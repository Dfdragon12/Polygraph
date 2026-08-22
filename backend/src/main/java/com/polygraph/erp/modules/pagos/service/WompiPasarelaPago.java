package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.pagos.entity.OrdenCompra;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.URLEncoder;

/**
 * Integración con Wompi Web Checkout (redirect firmado). Si no hay llave pública configurada,
 * opera en modo simulado: en vez de redirigir a Wompi, genera una URL al simulador propio del
 * frontend — así el flujo completo (orden → pago → saldo) se puede probar sin credenciales reales.
 * Pasar a producción real es solo cuestión de configurar las variables WOMPI_* — no requiere cambios de código.
 */
@Slf4j
@Service
public class WompiPasarelaPago implements PasarelaPago {

    private final String publicKey;
    private final String integritySecret;
    private final String eventsSecret;
    private final String checkoutUrl;
    private final String frontendUrl;

    public WompiPasarelaPago(
            @Value("${app.wompi.public-key}") String publicKey,
            @Value("${app.wompi.integrity-secret}") String integritySecret,
            @Value("${app.wompi.events-secret}") String eventsSecret,
            @Value("${app.wompi.checkout-url}") String checkoutUrl,
            @Value("${app.frontend.url}") String frontendUrl) {
        this.publicKey = publicKey;
        this.integritySecret = integritySecret;
        this.eventsSecret = eventsSecret;
        this.checkoutUrl = checkoutUrl;
        this.frontendUrl = frontendUrl;
    }

    /** Verdadero cuando no hay llave pública configurada — el simulador de pagos queda habilitado. */
    public boolean esModoSimulado() {
        return publicKey == null || publicKey.isBlank();
    }

    /** Verdadero cuando hay un secreto de eventos configurado, requisito para aceptar webhooks reales. */
    public boolean eventsSecretConfigurado() {
        return eventsSecret != null && !eventsSecret.isBlank();
    }

    /** Recalcula el checksum de un evento de Wompi (cadena de propiedades + timestamp + secreto) y lo compara en tiempo constante. */
    public boolean validarFirmaEvento(String cadenaSinSecreto, String checksumEsperado) {
        if (checksumEsperado == null) return false;
        String calculado = sha256Hex(cadenaSinSecreto + eventsSecret);
        return MessageDigest.isEqual(
                calculado.getBytes(StandardCharsets.UTF_8),
                checksumEsperado.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public IniciarPagoResultado iniciarPago(OrdenCompra orden) {
        if (esModoSimulado()) {
            String url = frontendUrl + "/cliente/comprar-servicios/simulador/" + orden.getIdOrdenCompra();
            log.info("Wompi en modo simulado — orden={}, url={}", orden.getIdOrdenCompra(), url);
            return new IniciarPagoResultado(url, true);
        }

        long amountInCents = orden.getMontoTotal().multiply(BigDecimal.valueOf(100)).longValueExact();
        String firma = firmarIntegridad(orden.getReferencia(), amountInCents, "COP");
        String redirectUrl = frontendUrl + "/cliente/comprar-servicios/resultado/" + orden.getIdOrdenCompra();

        String url = checkoutUrl
                + "?public-key=" + encode(publicKey)
                + "&currency=COP"
                + "&amount-in-cents=" + amountInCents
                + "&reference=" + encode(orden.getReferencia())
                + "&signature:integrity=" + encode(firma)
                + "&redirect-url=" + encode(redirectUrl);

        log.info("Orden de pago Wompi iniciada — orden={}, referencia={}", orden.getIdOrdenCompra(), orden.getReferencia());
        return new IniciarPagoResultado(url, false);
    }

    private String firmarIntegridad(String referencia, long amountInCents, String moneda) {
        String cadena = referencia + amountInCents + moneda + integritySecret;
        return sha256Hex(cadena);
    }

    private String sha256Hex(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    private String encode(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
