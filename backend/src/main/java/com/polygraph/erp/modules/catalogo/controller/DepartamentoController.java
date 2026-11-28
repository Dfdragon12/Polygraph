package com.polygraph.erp.modules.catalogo.controller;

import com.polygraph.erp.modules.catalogo.dto.DepartamentoRequest;
import com.polygraph.erp.modules.catalogo.dto.DepartamentoResponse;
import com.polygraph.erp.modules.catalogo.service.DepartamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Catálogo de departamentos — gestionable por ADMIN_POLYGRAPH (agregar/editar/eliminar); el
 *  listado es público porque lo consume el selector de "departamento" de los formularios sin
 *  sesión (candidato vía link, registro de cliente). */
@RestController
@RequestMapping("/api/v1/departamentos")
@RequiredArgsConstructor
public class DepartamentoController {

    private final DepartamentoService service;

    @GetMapping
    public ResponseEntity<List<DepartamentoResponse>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<DepartamentoResponse> crear(@Valid @RequestBody DepartamentoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<DepartamentoResponse> actualizar(
            @PathVariable Integer id, @Valid @RequestBody DepartamentoRequest req) {
        return ResponseEntity.ok(service.actualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
