package com.polygraph.erp.modules.catalogo.controller;

import com.polygraph.erp.modules.catalogo.dto.MotivoSubprocesoRequest;
import com.polygraph.erp.modules.catalogo.dto.MotivoSubprocesoResponse;
import com.polygraph.erp.modules.catalogo.service.MotivoSubprocesoService;
import com.polygraph.erp.shared.enums.TipoMotivo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Motivos de reprogramación, cancelación y devolución. Leer los activos: cualquier usuario interno; administrarlos: el admin. */
@RestController
@RequestMapping("/api/v1/catalogo/motivos")
@RequiredArgsConstructor
public class MotivoSubprocesoController {

    private final MotivoSubprocesoService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH','GESTOR','PROGRAMADOR','ANALISTA_INTERNO','POLIGRAFISTA','VISITADOR')")
    public ResponseEntity<List<MotivoSubprocesoResponse>> activos(@RequestParam TipoMotivo tipo) {
        return ResponseEntity.ok(service.listarActivos(tipo));
    }

    @GetMapping("/todos")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<List<MotivoSubprocesoResponse>> todos() {
        return ResponseEntity.ok(service.listarTodos());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<MotivoSubprocesoResponse> crear(@Valid @RequestBody MotivoSubprocesoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<MotivoSubprocesoResponse> actualizar(@PathVariable Integer id, @Valid @RequestBody MotivoSubprocesoRequest req) {
        return ResponseEntity.ok(service.actualizar(id, req));
    }
}
