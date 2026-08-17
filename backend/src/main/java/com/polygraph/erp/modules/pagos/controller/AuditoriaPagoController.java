package com.polygraph.erp.modules.pagos.controller;

import com.polygraph.erp.modules.pagos.dto.AuditoriaPagoResponse;
import com.polygraph.erp.modules.pagos.service.AuditoriaPagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pagos/auditoria")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class AuditoriaPagoController {

    private final AuditoriaPagoService auditoriaPagoService;

    @GetMapping
    public ResponseEntity<Page<AuditoriaPagoResponse>> listar(
            @RequestParam(required = false) String referencia,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auditoriaPagoService.listar(referencia, PageRequest.of(page, size)));
    }
}
