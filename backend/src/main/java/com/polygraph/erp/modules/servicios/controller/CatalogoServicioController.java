package com.polygraph.erp.modules.servicios.controller;

import com.polygraph.erp.modules.servicios.dto.CatalogoServicioRequest;
import com.polygraph.erp.modules.servicios.dto.CatalogoServicioResponse;
import com.polygraph.erp.modules.servicios.service.CatalogoServicioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class CatalogoServicioController {

    private final CatalogoServicioService servicio;

    @GetMapping
    public ResponseEntity<List<CatalogoServicioResponse>> listarTodos() {
        return ResponseEntity.ok(servicio.listarActivos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CatalogoServicioResponse> obtenerPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(servicio.obtenerPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<CatalogoServicioResponse> crear(
            @Valid @RequestBody CatalogoServicioRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(solicitud));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<CatalogoServicioResponse> actualizar(
            @PathVariable Integer id, @Valid @RequestBody CatalogoServicioRequest solicitud) {
        return ResponseEntity.ok(servicio.actualizar(id, solicitud));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        servicio.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
