package com.polygraph.erp.modules.pagos.controller;

import com.polygraph.erp.modules.pagos.dto.ComprobanteResponse;
import com.polygraph.erp.modules.pagos.dto.OrdenCompraResponse;
import com.polygraph.erp.modules.pagos.dto.ResumenPagosOrdenesResponse;
import com.polygraph.erp.modules.pagos.service.OrdenCompraService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Gestión de órdenes de compra y comprobantes para ADMIN_POLYGRAPH / GESTOR (ver reglas del
 * módulo de pagos en CLAUDE.md). La generación del comprobante es manual — a propósito, no
 * automática al aprobarse el pago. */
@RestController
@RequestMapping("/api/v1/admin/ordenes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH', 'GESTOR')")
public class AdminOrdenCompraController {

    private final OrdenCompraService ordenCompraService;

    @GetMapping
    public ResponseEntity<Page<OrdenCompraResponse>> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer idCliente,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ordenCompraService.listarTodasLasOrdenes(estado, idCliente, PageRequest.of(page, size)));
    }

    @GetMapping("/sin-facturar")
    public ResponseEntity<Page<OrdenCompraResponse>> listarSinFacturar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ordenCompraService.listarOrdenesSinFacturar(PageRequest.of(page, size)));
    }

    @GetMapping("/resumen")
    public ResponseEntity<ResumenPagosOrdenesResponse> resumen() {
        return ResponseEntity.ok(ordenCompraService.resumenPagos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrdenCompraResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ordenCompraService.obtenerOrdenAdmin(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        ordenCompraService.eliminarOrdenPendiente(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/comprobante")
    public ResponseEntity<ComprobanteResponse> generarComprobante(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ordenCompraService.generarComprobante(id));
    }

    @GetMapping("/comprobantes/{id}")
    public ResponseEntity<ComprobanteResponse> obtenerComprobante(@PathVariable Integer id) {
        return ResponseEntity.ok(ordenCompraService.obtenerComprobanteAdmin(id));
    }
}
