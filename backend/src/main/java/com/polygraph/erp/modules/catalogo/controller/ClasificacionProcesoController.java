package com.polygraph.erp.modules.catalogo.controller;

import com.polygraph.erp.modules.catalogo.dto.ClasificacionProcesoRequest;
import com.polygraph.erp.modules.catalogo.dto.ClasificacionProcesoResponse;
import com.polygraph.erp.modules.catalogo.service.ClasificacionProcesoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalogo/clasificaciones")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class ClasificacionProcesoController {

    private final ClasificacionProcesoService service;

    @GetMapping
    public ResponseEntity<List<ClasificacionProcesoResponse>> listar() {
        return ResponseEntity.ok(service.listarTodas());
    }

    @PostMapping
    public ResponseEntity<ClasificacionProcesoResponse> crear(@Valid @RequestBody ClasificacionProcesoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClasificacionProcesoResponse> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody ClasificacionProcesoRequest req) {
        return ResponseEntity.ok(service.actualizar(id, req));
    }

    @PatchMapping("/{id}/activar")
    public ResponseEntity<ClasificacionProcesoResponse> activar(@PathVariable Integer id) {
        return ResponseEntity.ok(service.cambiarEstado(id, true));
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<ClasificacionProcesoResponse> desactivar(@PathVariable Integer id) {
        return ResponseEntity.ok(service.cambiarEstado(id, false));
    }
}
