package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.pagos.config.WompiProperties;
import com.polygraph.erp.modules.pagos.entity.OrdenCompra;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Integración con Wompi Web Checkout (redirect firmado) para el carrito prepago (OrdenCompra). La
 * firma y la construcción de la URL viven en {@link WompiSignatureService} — compartidas con el
 * flujo genérico de pagos (PagoWompi) — este servicio solo resuelve los datos propios de una orden.
 * Si no hay llave pública configurada, opera en modo simulado: en vez de redirigir a Wompi, genera
 * una URL al simulador propio del frontend — así el flujo completo (orden → pago → saldo) se puede
 * probar sin credenciales reales. Pasar a producción real es solo cuestión de configurar las
 * variables WOMPI_* — no requiere cambios de código.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WompiPasarelaPago implements PasarelaPago {

    private final WompiProperties wompiProperties;
    private final WompiSignatureService wompiSignatureService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    /** Verdadero cuando no hay llave pública configurada — el simulador de pagos queda habilitado. */
    public boolean esModoSimulado() {
        String publicKey = wompiProperties.getPublicKey();
        return publicKey == null || publicKey.isBlank();
    }

    @Override
    public IniciarPagoResultado iniciarPago(OrdenCompra orden) {
        if (esModoSimulado()) {
            String url = frontendUrl + "/cliente/comprar-servicios/simulador/" + orden.getIdOrdenCompra();
            log.info("Wompi en modo simulado — orden={}, url={}", orden.getIdOrdenCompra(), url);
            return new IniciarPagoResultado(url, true);
        }

        long amountInCents = orden.getMontoTotal().multiply(BigDecimal.valueOf(100)).longValueExact();
        String redirectUrl = frontendUrl + "/cliente/comprar-servicios/resultado/" + orden.getIdOrdenCompra();
        String url = wompiSignatureService.construirUrlCheckout(orden.getReferencia(), amountInCents, "COP", redirectUrl);

        log.info("Orden de pago Wompi iniciada — orden={}, referencia={}", orden.getIdOrdenCompra(), orden.getReferencia());
        return new IniciarPagoResultado(url, false);
    }
}
