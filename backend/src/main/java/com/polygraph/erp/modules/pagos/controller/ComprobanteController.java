package com.polygraph.erp.modules.pagos.controller;

import com.polygraph.erp.modules.pagos.dto.ComprobanteResponse;
import com.polygraph.erp.modules.pagos.service.OrdenCompraService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Comprobantes de compra internos — no son factura electrónica DIAN (ver ComprobanteCompra). */
@RestController
@RequestMapping("/api/v1/client/comprobantes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_CLIENTE')")
public class ComprobanteController {

    private final OrdenCompraService ordenCompraService;

    @GetMapping
    public ResponseEntity<List<ComprobanteResponse>> listar(Authentication auth) {
        return ResponseEntity.ok(ordenCompraService.listarComprobantes(auth.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ComprobanteResponse> obtener(@PathVariable Integer id, Authentication auth) {
        return ResponseEntity.ok(ordenCompraService.obtenerComprobante(id, auth.getName()));
    }
}
