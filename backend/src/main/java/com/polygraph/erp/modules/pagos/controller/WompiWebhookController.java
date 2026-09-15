package com.polygraph.erp.modules.pagos.controller;

import com.polygraph.erp.modules.pagos.service.PagoWompiWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Único webhook público de Wompi en todo el sistema — sin JWT, ver SecurityConfig (permitAll solo
 * para estas dos rutas). La autenticidad la da exclusivamente el checksum de la firma (ver
 * {@link PagoWompiWebhookService}), nunca el redirect del navegador. Responde siempre rápido: el
 * post-procesamiento pesado de un pago aprobado se dispara async.
 *
 * Acepta DOS rutas apuntando al mismo método — no son dos webhooks, es un solo controlador que
 * responde en ambas: "/api/v1/pagos/wompi/webhook" (la que se documentó como definitiva) y
 * "/api/v1/webhooks/wompi" (la que en la práctica quedó configurada en el panel de Wompi — se
 * confirmó por el log de eventos ahí, con "URL del evento" apuntando a esta última y 404 mientras
 * solo existía la primera). Así funciona sin importar cuál de las dos tengan guardada en Wompi.
 *
 * Procesa eventos tanto del flujo genérico de pagos (PagoWompi — facturas pospago) como del carrito
 * prepago (OrdenCompra) — {@link PagoWompiWebhookService#procesarEvento} intenta primero el genérico
 * y cae automáticamente al de OrdenCompra si la referencia no le pertenece, así no importa cuál de
 * los dos flujos originó el pago.
 */
@RestController
@RequiredArgsConstructor
public class WompiWebhookController {

    private final PagoWompiWebhookService webhookService;

    @PostMapping({"/api/v1/pagos/wompi/webhook", "/api/v1/webhooks/wompi"})
    public ResponseEntity<Void> webhook(@RequestBody String rawBody, HttpServletRequest request) {
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
