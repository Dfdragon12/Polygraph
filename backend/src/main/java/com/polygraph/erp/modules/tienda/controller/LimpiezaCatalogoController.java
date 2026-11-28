package com.polygraph.erp.modules.tienda.controller;

import com.polygraph.erp.modules.tienda.service.LimpiezaCatalogoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/** TP-10: limpieza del catálogo actual. Solo el administrador; aplicar exige escribir «CONFIRMO». */
@RestController
@RequestMapping("/api/v1/admin/catalogo/limpieza")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class LimpiezaCatalogoController {

    private final LimpiezaCatalogoService service;

    public record AplicarRequest(String confirmacion, Set<String> pasos) {}

    /** Qué se haría y el reporte de servicios sin precio base o sin textos. No guarda nada. */
    @GetMapping
    public ResponseEntity<LimpiezaCatalogoService.Plan> simular() {
        return ResponseEntity.ok(service.simular());
    }

    @PostMapping("/aplicar")
    public ResponseEntity<LimpiezaCatalogoService.Resultado> aplicar(@RequestBody AplicarRequest body, Authentication auth) {
        return ResponseEntity.ok(service.aplicar(body.confirmacion(), body.pasos(), auth.getName()));
    }
}
