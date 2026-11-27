package com.polygraph.erp.modules.trabajos.controller;

import com.polygraph.erp.modules.trabajos.service.DiagnosticoServidorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PC-08 · Admin → Trabajos → «Diagnóstico del servidor»: qué falta o está mal configurado, y una prueba real de generación de PDF. */
@RestController
@RequestMapping("/api/v1/admin/sistema")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class AdminSistemaController {

    private final DiagnosticoServidorService diagnostico;

    @GetMapping("/diagnostico")
    public ResponseEntity<DiagnosticoServidorService.Diagnostico> diagnostico() {
        return ResponseEntity.ok(diagnostico.diagnostico());
    }

    @PostMapping("/probar-pdf")
    public ResponseEntity<DiagnosticoServidorService.PruebaPdf> probarPdf() {
        return ResponseEntity.ok(diagnostico.probarPdf());
    }
}
