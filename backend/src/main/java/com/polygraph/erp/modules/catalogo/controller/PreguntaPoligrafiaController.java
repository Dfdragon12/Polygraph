package com.polygraph.erp.modules.catalogo.controller;

import com.polygraph.erp.modules.catalogo.dto.PreguntaPoligrafiaRequest;
import com.polygraph.erp.modules.catalogo.dto.PreguntaPoligrafiaResponse;
import com.polygraph.erp.modules.catalogo.service.PreguntaPoligrafiaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Administración de los temas de poligrafía que el cliente puede pedir evaluar. */
@RestController
@RequestMapping("/api/v1/catalogo/preguntas-poligrafia")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class PreguntaPoligrafiaController {

    private final PreguntaPoligrafiaService service;

    @GetMapping
    public ResponseEntity<List<PreguntaPoligrafiaResponse>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @PostMapping
    public ResponseEntity<PreguntaPoligrafiaResponse> crear(@Valid @RequestBody PreguntaPoligrafiaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PreguntaPoligrafiaResponse> actualizar(@PathVariable Integer id,
                                                                 @Valid @RequestBody PreguntaPoligrafiaRequest req) {
        return ResponseEntity.ok(service.actualizar(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
