package com.polygraph.erp.modules.catalogo.controller;

import com.polygraph.erp.modules.catalogo.dto.ItemHojaVidaRequest;
import com.polygraph.erp.modules.catalogo.dto.ItemHojaVidaResponse;
import com.polygraph.erp.modules.catalogo.service.ItemHojaVidaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalogo/hoja-vida")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class ItemHojaVidaController {

    private final ItemHojaVidaService service;

    @GetMapping
    public ResponseEntity<List<ItemHojaVidaResponse>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @PostMapping
    public ResponseEntity<ItemHojaVidaResponse> crear(@Valid @RequestBody ItemHojaVidaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ItemHojaVidaResponse> actualizar(@PathVariable Integer id,
                                                           @Valid @RequestBody ItemHojaVidaRequest req) {
        return ResponseEntity.ok(service.actualizar(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
