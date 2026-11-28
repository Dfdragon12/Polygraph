package com.polygraph.erp.modules.catalogo.controller;

import com.polygraph.erp.modules.catalogo.dto.BarrioRequest;
import com.polygraph.erp.modules.catalogo.dto.BarrioResponse;
import com.polygraph.erp.modules.catalogo.service.BarrioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/barrios")
@RequiredArgsConstructor
public class BarrioController {

    private final BarrioService service;

    // idCiudad es opcional: sin él, devuelve el catálogo completo — usado por el selector de
    // barrio "libre" (sin ciudad fija) de las plantillas de formulario dinámicas (ver
    // TipoDatoFormulario.BARRIO), donde no hay necesariamente un campo de ciudad emparejado.
    @GetMapping
    public ResponseEntity<List<BarrioResponse>> listarPorCiudad(@RequestParam(required = false) Integer idCiudad) {
        return ResponseEntity.ok(service.listarPorCiudad(idCiudad));
    }

    // Público — el formulario del candidato/cliente no siempre tiene sesión y necesita poder
    // agregar su barrio si no está en el catálogo (mismo criterio que POST /ciudades).
    @PostMapping
    public ResponseEntity<BarrioResponse> crear(@Valid @RequestBody BarrioRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req));
    }

    @PatchMapping("/{id}/confirmar")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<BarrioResponse> confirmar(@PathVariable Integer id) {
        return ResponseEntity.ok(service.confirmar(id));
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
