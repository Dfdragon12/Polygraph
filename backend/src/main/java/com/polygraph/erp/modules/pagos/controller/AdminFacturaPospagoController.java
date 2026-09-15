package com.polygraph.erp.modules.pagos.controller;

import com.polygraph.erp.modules.pagos.dto.EstadoCuentaPospagoResponse;
import com.polygraph.erp.modules.pagos.dto.FacturaPospagoDetalleResponse;
import com.polygraph.erp.modules.pagos.dto.FacturaPospagoResponse;
import com.polygraph.erp.modules.pagos.dto.GenerarFacturaPospagoRequest;
import com.polygraph.erp.modules.pagos.dto.MovimientoCreditoPospagoResponse;
import com.polygraph.erp.modules.pagos.service.ClientePospagoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Gestión admin del crédito pospago: estado de cuenta por cliente, generación de facturas de corte y listado global. */
@RestController
@RequestMapping("/api/v1/admin/pospago")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH', 'GESTOR')")
public class AdminFacturaPospagoController {

    private final ClientePospagoService clientePospagoService;

    @GetMapping("/clientes/{idCliente}/estado-cuenta")
    public ResponseEntity<EstadoCuentaPospagoResponse> estadoCuenta(@PathVariable Integer idCliente) {
        return ResponseEntity.ok(clientePospagoService.obtenerEstadoCuenta(idCliente));
    }

    @GetMapping("/clientes/{idCliente}/movimientos-pendientes")
    public ResponseEntity<List<MovimientoCreditoPospagoResponse>> movimientosPendientes(@PathVariable Integer idCliente) {
        return ResponseEntity.ok(clientePospagoService.listarMovimientosPendientesDeFacturar(idCliente));
    }

    @GetMapping("/clientes/{idCliente}/facturas")
    public ResponseEntity<List<FacturaPospagoResponse>> facturasDelCliente(@PathVariable Integer idCliente) {
        return ResponseEntity.ok(clientePospagoService.listarFacturas(idCliente));
    }

    @PostMapping("/clientes/{idCliente}/facturas")
    public ResponseEntity<FacturaPospagoResponse> generarFactura(
            @PathVariable Integer idCliente, @Valid @RequestBody GenerarFacturaPospagoRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clientePospagoService.generarFactura(idCliente, request.fechaVencimiento(), auth.getName()));
    }

    @GetMapping("/facturas")
    public ResponseEntity<Page<FacturaPospagoResponse>> listarTodasLasFacturas(
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(clientePospagoService.listarTodasLasFacturas(estado, PageRequest.of(page, size)));
    }

    @GetMapping("/facturas/{id}")
    public ResponseEntity<FacturaPospagoDetalleResponse> obtenerFactura(@PathVariable Long id) {
        return ResponseEntity.ok(clientePospagoService.obtenerFacturaConDetalle(id));
    }

    @DeleteMapping("/facturas/{id}")
    public ResponseEntity<Void> eliminarFactura(@PathVariable Long id) {
        clientePospagoService.eliminarFacturaPendiente(id);
        return ResponseEntity.noContent().build();
    }
}
