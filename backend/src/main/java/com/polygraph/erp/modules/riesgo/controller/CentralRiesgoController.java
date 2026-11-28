package com.polygraph.erp.modules.riesgo.controller;

import com.polygraph.erp.modules.riesgo.dto.CentralRiesgoDtos.CentralRequest;
import com.polygraph.erp.modules.riesgo.dto.CentralRiesgoDtos.CentralResponse;
import com.polygraph.erp.modules.riesgo.service.CentralRiesgoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Centrales de riesgo y bandas de su indicador de pago — admin → Calificación de riesgo → Centrales de riesgo. */
@RestController
@RequestMapping("/api/v1/catalogo/riesgo/centrales")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class CentralRiesgoController {

    private final CentralRiesgoService service;

    @GetMapping
    public ResponseEntity<List<CentralResponse>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @PostMapping
    public ResponseEntity<CentralResponse> crear(@Valid @RequestBody CentralRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CentralResponse> actualizar(@PathVariable Integer id, @Valid @RequestBody CentralRequest req) {
        return ResponseEntity.ok(service.actualizar(id, req));
    }
}
