package com.polygraph.erp.modules.catalogo.controller;

import com.polygraph.erp.modules.catalogo.dto.DescuentoRequest;
import com.polygraph.erp.modules.catalogo.dto.DescuentoResponse;
import com.polygraph.erp.modules.catalogo.service.DescuentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/descuentos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class DescuentoController {

    private final DescuentoService descuentoService;

    @GetMapping
    public ResponseEntity<List<DescuentoResponse>> listar() {
        return ResponseEntity.ok(descuentoService.listar());
    }

    @PostMapping
    public ResponseEntity<DescuentoResponse> crear(@Valid @RequestBody DescuentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(descuentoService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DescuentoResponse> actualizar(@PathVariable Long id, @Valid @RequestBody DescuentoRequest request) {
        return ResponseEntity.ok(descuentoService.actualizar(id, request));
    }

    @PatchMapping("/{id}/activo")
    public ResponseEntity<DescuentoResponse> cambiarActivo(@PathVariable Long id, @RequestParam boolean activo) {
        return ResponseEntity.ok(descuentoService.cambiarActivo(id, activo));
    }
}
