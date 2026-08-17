package com.polygraph.erp.modules.pagos.controller;

import com.polygraph.erp.modules.pagos.service.OrdenCompraService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook público de Wompi (sin JWT — Wompi no autentica como nuestros clientes).
 * La seguridad la da la validación del checksum de firma dentro de {@link OrdenCompraService#procesarEventoWompi}.
 */
@RestController
@RequestMapping("/api/v1/pagos/wompi")
@RequiredArgsConstructor
public class WompiWebhookController {

    private final OrdenCompraService ordenCompraService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String rawBody) {
        ordenCompraService.procesarEventoWompi(rawBody);
        return ResponseEntity.ok().build();
    }
}
