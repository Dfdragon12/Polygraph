package com.polygraph.erp.modules.catalogo.controller;

import com.polygraph.erp.modules.catalogo.dto.CiudadRequest;
import com.polygraph.erp.modules.catalogo.dto.CiudadResponse;
import com.polygraph.erp.modules.catalogo.dto.ClasificarCiudadRequest;
import com.polygraph.erp.modules.catalogo.dto.ConfirmarCiudadRequest;
import com.polygraph.erp.modules.catalogo.service.CiudadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ciudades")
@RequiredArgsConstructor
public class CiudadController {

    private final CiudadService service;

    @GetMapping
    public ResponseEntity<List<CiudadResponse>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    // El listado de departamentos vive ahora en /api/v1/departamentos (DepartamentoController) —
    // catálogo gestionable por admin, ya no una lista fija hardcodeada.

    // Público — el formulario del evaluado no tiene sesión y necesita poder agregar su ciudad
    // si no está en el catálogo.
    @PostMapping
    public ResponseEntity<CiudadResponse> crear(@Valid @RequestBody CiudadRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req));
    }

    @PatchMapping("/{id}/nivel")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<CiudadResponse> clasificar(
            @PathVariable Integer id,
            @Valid @RequestBody ClasificarCiudadRequest req) {
        return ResponseEntity.ok(service.clasificar(id, req));
    }

    @PatchMapping("/{id}/confirmar")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<CiudadResponse> confirmar(
            @PathVariable Integer id, @Valid @RequestBody(required = false) ConfirmarCiudadRequest req) {
        return ResponseEntity.ok(service.confirmar(id, req));
    }

    @GetMapping("/pendientes/conteo")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<Map<String, Long>> contarPendientes() {
        return ResponseEntity.ok(Map.of("pendientes", service.contarPendientes()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
