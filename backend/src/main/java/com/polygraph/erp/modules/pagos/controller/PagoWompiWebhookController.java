package com.polygraph.erp.modules.pagos.controller;

import com.polygraph.erp.modules.pagos.service.PagoWompiWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook público de Wompi para el módulo de pagos (PagoWompi) — sin JWT, ver SecurityConfig
 * (permitAll solo para POST /api/v1/webhooks/wompi). La autenticidad la da exclusivamente
 * el checksum de la firma (ver {@link PagoWompiWebhookService}), nunca el redirect del navegador.
 * Responde siempre rápido: el post-procesamiento pesado de un pago aprobado se dispara async.
 *
 * Nombrado "PagoWompiWebhookController" (no "WompiWebhookController") para no chocar como bean
 * de Spring con {@link WompiWebhookController} — el webhook del flujo viejo de carrito/OrdenCompra,
 * que vive en este mismo paquete. Spring nombra los beans por el nombre simple de la clase, no
 * por el paquete completo, así que dos clases con el mismo nombre simple colisionan igual aunque
 * estén en paquetes distintos.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class PagoWompiWebhookController {

    private final PagoWompiWebhookService webhookService;

    @PostMapping("/wompi")
    public ResponseEntity<Void> recibirEventoWompi(@RequestBody String rawBody, HttpServletRequest request) {
        boolean firmaValida = webhookService.procesarEvento(rawBody, ipOrigen(request));
        return firmaValida ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    private String ipOrigen(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
