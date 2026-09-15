package com.polygraph.erp.modules.pagos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.polygraph.erp.modules.pagos.config.WompiProperties;
import com.polygraph.erp.modules.pagos.dto.WompiEventDTO;
import com.polygraph.erp.modules.pagos.dto.WompiEventSignatureDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Firma, verificación de integridad y construcción de URLs del Web Checkout de Wompi. Único lugar
 * que hace estas dos cosas — tanto el flujo de carrito prepago (OrdenCompra) como el genérico
 * (PagoWompi, facturas pospago) pasan por aquí, para no duplicar la lógica de firma/URL entre los dos.
 * No registra integritySecret ni eventsSecret en logs.
 */
@Service
@RequiredArgsConstructor
public class WompiSignatureService {

    private final WompiProperties wompiProperties;

    /** Firma de integridad para el Web Checkout: referencia + montoEnCentavos + moneda + integritySecret. */
    public String generarFirmaIntegridad(String referencia, Long montoEnCentavos, String moneda) {
        String cadena = referencia + montoEnCentavos + moneda + wompiProperties.getIntegritySecret();
        return sha256Hex(cadena);
    }

    /** URL completa y firmada del Web Checkout de Wompi — redirect de página completa (nunca se arma en el frontend). */
    public String construirUrlCheckout(String referencia, long montoEnCentavos, String moneda, String redirectUrl) {
        String firma = generarFirmaIntegridad(referencia, montoEnCentavos, moneda);
        return wompiProperties.getCheckoutUrl()
                + "?public-key=" + encode(wompiProperties.getPublicKey())
                + "&currency=" + moneda
                + "&amount-in-cents=" + montoEnCentavos
                + "&reference=" + encode(referencia)
                + "&signature:integrity=" + encode(firma)
                + "&redirect-url=" + encode(redirectUrl);
    }

    private String encode(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    /** Valida el checksum de un evento de webhook resolviendo dinámicamente signature.properties sobre data. */
    public boolean validarChecksumEvento(WompiEventDTO evento) {
        if (evento == null || evento.signature() == null) {
            return false;
        }

        WompiEventSignatureDTO firma = evento.signature();
        if (firma.checksum() == null || firma.properties() == null) {
            return false;
        }

        StringBuilder cadena = new StringBuilder();
        for (String propiedad : firma.properties()) {
            cadena.append(resolverValor(evento.data(), propiedad));
        }
        cadena.append(evento.timestamp());
        cadena.append(wompiProperties.getEventsSecret());

        String calculado = sha256Hex(cadena.toString());
        return MessageDigest.isEqual(
                calculado.getBytes(StandardCharsets.UTF_8),
                firma.checksum().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private String resolverValor(JsonNode data, String ruta) {
        if (data == null || ruta == null) {
            return "";
        }
        JsonNode actual = data;
        for (String segmento : ruta.split("\\.")) {
            actual = actual.path(segmento);
        }
        return actual.asText("");
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
}
