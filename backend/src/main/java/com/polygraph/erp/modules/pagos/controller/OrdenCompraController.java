package com.polygraph.erp.modules.pagos.controller;

import com.polygraph.erp.modules.pagos.dto.*;
import com.polygraph.erp.modules.pagos.service.OrdenCompraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/client/ordenes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_CLIENTE')")
public class OrdenCompraController {

    private final OrdenCompraService ordenCompraService;

    @PostMapping
    public ResponseEntity<OrdenCompraResponse> crear(@Valid @RequestBody CrearOrdenRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ordenCompraService.crearOrden(request, auth.getName()));
    }

    @PostMapping("/{id}/pagar")
    public ResponseEntity<IniciarPagoResponse> pagar(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(ordenCompraService.iniciarPago(id, auth.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrdenCompraResponse> obtener(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(ordenCompraService.obtenerOrden(id, auth.getName()));
    }

    @GetMapping
    public ResponseEntity<Page<OrdenCompraResponse>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(ordenCompraService.listarOrdenes(auth.getName(), PageRequest.of(page, size)));
    }

    @PostMapping("/{id}/simular")
    public ResponseEntity<OrdenCompraResponse> simular(
            @PathVariable Long id,
            @Valid @RequestBody SimulacionPagoRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ordenCompraService.simularPago(id, request, auth.getName()));
    }
}
