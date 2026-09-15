package com.polygraph.erp.modules.pagos.controller;

import com.polygraph.erp.modules.pagos.dto.EstadoCuentaPospagoResponse;
import com.polygraph.erp.modules.pagos.dto.FacturaPospagoDetalleResponse;
import com.polygraph.erp.modules.pagos.dto.FacturaPospagoResponse;
import com.polygraph.erp.modules.pagos.service.ClientePospagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Portal de crédito pospago del cliente — igual que el módulo de pagos, solo ADMIN_CLIENTE accede (ver CLAUDE.md). */
@RestController
@RequestMapping("/api/v1/client/pospago")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_CLIENTE')")
public class FacturaPospagoController {

    private final ClientePospagoService clientePospagoService;

    @GetMapping("/estado-cuenta")
    public ResponseEntity<EstadoCuentaPospagoResponse> estadoCuenta(Authentication auth) {
        return ResponseEntity.ok(clientePospagoService.obtenerEstadoCuenta(auth.getName()));
    }

    @GetMapping("/facturas")
    public ResponseEntity<List<FacturaPospagoResponse>> listarFacturas(Authentication auth) {
        return ResponseEntity.ok(clientePospagoService.listarFacturas(auth.getName()));
    }

    @GetMapping("/facturas/{id}")
    public ResponseEntity<FacturaPospagoDetalleResponse> obtenerFactura(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(clientePospagoService.obtenerFacturaConDetalle(id, auth.getName()));
    }
}
