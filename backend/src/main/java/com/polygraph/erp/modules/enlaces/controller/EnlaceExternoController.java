package com.polygraph.erp.modules.enlaces.controller;

import com.polygraph.erp.modules.enlaces.dto.EnlaceExternoRequest;
import com.polygraph.erp.modules.enlaces.dto.EnlaceExternoResponse;
import com.polygraph.erp.modules.enlaces.service.EnlaceExternoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/enlaces")
@RequiredArgsConstructor
public class EnlaceExternoController {

    private final EnlaceExternoService servicio;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EnlaceExternoResponse>> listar() {
        return ResponseEntity.ok(servicio.listarActivos());
    }

    @GetMapping("/todos")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<List<EnlaceExternoResponse>> listarTodos() {
        return ResponseEntity.ok(servicio.listarTodos());
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EnlaceExternoResponse> crear(
            @Valid @RequestBody EnlaceExternoRequest req,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(req, auth.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EnlaceExternoResponse> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody EnlaceExternoRequest req) {
        return ResponseEntity.ok(servicio.actualizar(id, req));
    }

    @PatchMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<EnlaceExternoResponse> activar(@PathVariable Integer id) {
        return ResponseEntity.ok(servicio.cambiarEstado(id, true));
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<EnlaceExternoResponse> desactivar(@PathVariable Integer id) {
        return ResponseEntity.ok(servicio.cambiarEstado(id, false));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
