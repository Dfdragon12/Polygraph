package com.polygraph.erp.modules.pagos.controller;

import com.polygraph.erp.modules.pagos.dto.DocumentoFacturacionResponse;
import com.polygraph.erp.modules.pagos.service.FacturacionResumenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tabla unificada de "documentos de facturación" (órdenes prepago + facturas pospago mezcladas)
 *  que ve el admin en Facturacion.jsx — ver FacturacionResumenService para el detalle de cómo se
 *  combinan los dos conceptos. */
@RestController
@RequestMapping("/api/v1/admin/facturacion")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH', 'GESTOR')")
public class AdminFacturacionController {

    private final FacturacionResumenService facturacionResumenService;

    @GetMapping("/documentos")
    public ResponseEntity<Page<DocumentoFacturacionResponse>> listarDocumentos(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean porFacturar,
            @RequestParam(defaultValue = "false") boolean facturado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(facturacionResumenService.listarDocumentos(tipo, estado, q, porFacturar, facturado, PageRequest.of(page, size)));
    }
}
