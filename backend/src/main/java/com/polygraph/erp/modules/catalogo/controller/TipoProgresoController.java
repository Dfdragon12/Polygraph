package com.polygraph.erp.modules.catalogo.controller;

import com.polygraph.erp.modules.catalogo.dto.ImpactoProgresoResponse;
import com.polygraph.erp.modules.catalogo.dto.TipoProgresoRequest;
import com.polygraph.erp.modules.catalogo.dto.TipoProgresoResponse;
import com.polygraph.erp.modules.catalogo.service.TipoProgresoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalogo/tipos-progreso")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class TipoProgresoController {

    private final TipoProgresoService service;

    @GetMapping("/{id}/impacto")
    public ResponseEntity<ImpactoProgresoResponse> impacto(@PathVariable Integer id) {
        return ResponseEntity.ok(service.obtenerImpacto(id));
    }

    @GetMapping
    public ResponseEntity<List<TipoProgresoResponse>> listar() {
        return ResponseEntity.ok(service.listarTodos());
    }

    @PostMapping
    public ResponseEntity<TipoProgresoResponse> crear(@Valid @RequestBody TipoProgresoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TipoProgresoResponse> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody TipoProgresoRequest req) {
        return ResponseEntity.ok(service.actualizar(id, req));
    }

    @PatchMapping("/{id}/activar")
    public ResponseEntity<TipoProgresoResponse> activar(@PathVariable Integer id) {
        return ResponseEntity.ok(service.cambiarEstado(id, true));
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<TipoProgresoResponse> desactivar(@PathVariable Integer id) {
        return ResponseEntity.ok(service.cambiarEstado(id, false));
    }
}
