package com.polygraph.erp.modules.catalogo.controller;

import com.polygraph.erp.modules.catalogo.dto.CategoriaNovedadRequest;
import com.polygraph.erp.modules.catalogo.dto.CategoriaNovedadResponse;
import com.polygraph.erp.modules.catalogo.dto.TipoNovedadRequest;
import com.polygraph.erp.modules.catalogo.dto.TipoNovedadResponse;
import com.polygraph.erp.modules.catalogo.service.TipoNovedadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Administración del catálogo de novedades — solo ADMIN_POLYGRAPH. La lista que ven los
 *  ejecutores al reportar sale de GET /gestor/asignaciones/{id}/novedades (ya filtrada). */
@RestController
@RequestMapping("/api/v1/catalogo/novedades")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class TipoNovedadController {

    private final TipoNovedadService service;

    // ── Categorías ──
    @GetMapping("/categorias")
    public ResponseEntity<List<CategoriaNovedadResponse>> listarCategorias() {
        return ResponseEntity.ok(service.listarCategorias());
    }

    @PostMapping("/categorias")
    public ResponseEntity<CategoriaNovedadResponse> crearCategoria(@Valid @RequestBody CategoriaNovedadRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearCategoria(req));
    }

    @PutMapping("/categorias/{id}")
    public ResponseEntity<CategoriaNovedadResponse> actualizarCategoria(@PathVariable Integer id, @Valid @RequestBody CategoriaNovedadRequest req) {
        return ResponseEntity.ok(service.actualizarCategoria(id, req));
    }

    @PatchMapping("/categorias/{id}/activar")
    public ResponseEntity<CategoriaNovedadResponse> activarCategoria(@PathVariable Integer id) {
        return ResponseEntity.ok(service.cambiarEstadoCategoria(id, true));
    }

    @PatchMapping("/categorias/{id}/desactivar")
    public ResponseEntity<CategoriaNovedadResponse> desactivarCategoria(@PathVariable Integer id) {
        return ResponseEntity.ok(service.cambiarEstadoCategoria(id, false));
    }

    @DeleteMapping("/categorias/{id}")
    public ResponseEntity<Void> eliminarCategoria(@PathVariable Integer id) {
        service.eliminarCategoria(id);
        return ResponseEntity.noContent().build();
    }

    // ── Tipos ──
    @GetMapping("/tipos")
    public ResponseEntity<List<TipoNovedadResponse>> listarTipos() {
        return ResponseEntity.ok(service.listarTipos());
    }

    @PostMapping("/tipos")
    public ResponseEntity<TipoNovedadResponse> crearTipo(@Valid @RequestBody TipoNovedadRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearTipo(req));
    }

    @PutMapping("/tipos/{id}")
    public ResponseEntity<TipoNovedadResponse> actualizarTipo(@PathVariable Integer id, @Valid @RequestBody TipoNovedadRequest req) {
        return ResponseEntity.ok(service.actualizarTipo(id, req));
    }

    @PatchMapping("/tipos/{id}/activar")
    public ResponseEntity<TipoNovedadResponse> activarTipo(@PathVariable Integer id) {
        return ResponseEntity.ok(service.cambiarEstadoTipo(id, true));
    }

    @PatchMapping("/tipos/{id}/desactivar")
    public ResponseEntity<TipoNovedadResponse> desactivarTipo(@PathVariable Integer id) {
        return ResponseEntity.ok(service.cambiarEstadoTipo(id, false));
    }

    @DeleteMapping("/tipos/{id}")
    public ResponseEntity<Void> eliminarTipo(@PathVariable Integer id) {
        service.eliminarTipo(id);
        return ResponseEntity.noContent().build();
    }
}
