package com.polygraph.erp.modules.catalogo.controller;

import com.polygraph.erp.modules.catalogo.dto.*;
import com.polygraph.erp.modules.catalogo.service.ProcesoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalogo/procesos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class ProcesoController {

    private final ProcesoService service;

    @GetMapping
    public ResponseEntity<List<ProcesoResponse>> listar() {
        return ResponseEntity.ok(service.listarTodos());
    }

    @PostMapping
    public ResponseEntity<ProcesoResponse> crear(@Valid @RequestBody ProcesoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProcesoResponse> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody ProcesoRequest req) {
        return ResponseEntity.ok(service.actualizar(id, req));
    }

    @PatchMapping("/{id}/activar")
    public ResponseEntity<ProcesoResponse> activar(@PathVariable Integer id) {
        return ResponseEntity.ok(service.cambiarEstado(id, true));
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<ProcesoResponse> desactivar(@PathVariable Integer id) {
        return ResponseEntity.ok(service.cambiarEstado(id, false));
    }

    @GetMapping("/{id}/pasos")
    public ResponseEntity<List<PasoProcesoResponse>> obtenerPasos(@PathVariable Integer id) {
        return ResponseEntity.ok(service.obtenerPasos(id));
    }

    @PostMapping("/{id}/pasos")
    public ResponseEntity<PasoProcesoResponse> asignarPaso(
            @PathVariable Integer id,
            @Valid @RequestBody PasoProcesoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.asignarPaso(id, req));
    }

    @PutMapping("/{id}/pasos/{pasoId}")
    public ResponseEntity<PasoProcesoResponse> actualizarPaso(
            @PathVariable Integer id,
            @PathVariable Integer pasoId,
            @Valid @RequestBody PasoProcesoRequest req) {
        return ResponseEntity.ok(service.actualizarPaso(pasoId, req));
    }

    @DeleteMapping("/{id}/pasos/{pasoId}")
    public ResponseEntity<Void> eliminarPaso(
            @PathVariable Integer id,
            @PathVariable Integer pasoId) {
        service.eliminarPaso(pasoId);
        return ResponseEntity.noContent().build();
    }
}
