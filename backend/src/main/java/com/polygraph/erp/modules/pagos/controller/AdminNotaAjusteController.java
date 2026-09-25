package com.polygraph.erp.modules.pagos.controller;

import com.polygraph.erp.modules.pagos.dto.GenerarNotaAjusteRequest;
import com.polygraph.erp.modules.pagos.dto.NotaAjusteResponse;
import com.polygraph.erp.modules.pagos.service.NotaAjusteService;
import com.polygraph.erp.shared.enums.TipoNotaAjuste;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Notas crédito/débito internas sobre órdenes prepago y facturas pospago ya generadas — ver
 *  NotaAjusteService para el detalle de qué mueve cada una. */
@RestController
@RequestMapping("/api/v1/admin/facturacion/notas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH', 'GESTOR')")
public class AdminNotaAjusteController {

    private final NotaAjusteService notaAjusteService;

    @PostMapping("/ordenes/{idOrdenCompra}/credito")
    public ResponseEntity<NotaAjusteResponse> notaCreditoOrden(
            @PathVariable Long idOrdenCompra, @Valid @RequestBody GenerarNotaAjusteRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notaAjusteService.generarNotaOrden(idOrdenCompra, TipoNotaAjuste.CREDITO, request, auth.getName()));
    }

    @PostMapping("/ordenes/{idOrdenCompra}/debito")
    public ResponseEntity<NotaAjusteResponse> notaDebitoOrden(
            @PathVariable Long idOrdenCompra, @Valid @RequestBody GenerarNotaAjusteRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notaAjusteService.generarNotaOrden(idOrdenCompra, TipoNotaAjuste.DEBITO, request, auth.getName()));
    }

    @GetMapping("/ordenes/{idOrdenCompra}")
    public ResponseEntity<List<NotaAjusteResponse>> listarPorOrden(@PathVariable Long idOrdenCompra) {
        return ResponseEntity.ok(notaAjusteService.listarPorOrden(idOrdenCompra));
    }

    @PostMapping("/facturas/{idFactura}/credito")
    public ResponseEntity<NotaAjusteResponse> notaCreditoFactura(
            @PathVariable Long idFactura, @Valid @RequestBody GenerarNotaAjusteRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notaAjusteService.generarNotaFactura(idFactura, TipoNotaAjuste.CREDITO, request, auth.getName()));
    }

    @PostMapping("/facturas/{idFactura}/debito")
    public ResponseEntity<NotaAjusteResponse> notaDebitoFactura(
            @PathVariable Long idFactura, @Valid @RequestBody GenerarNotaAjusteRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notaAjusteService.generarNotaFactura(idFactura, TipoNotaAjuste.DEBITO, request, auth.getName()));
    }

    @GetMapping("/facturas/{idFactura}")
    public ResponseEntity<List<NotaAjusteResponse>> listarPorFactura(@PathVariable Long idFactura) {
        return ResponseEntity.ok(notaAjusteService.listarPorFactura(idFactura));
    }
}
