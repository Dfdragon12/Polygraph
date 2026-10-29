package com.polygraph.erp.modules.riesgo.controller;

import com.polygraph.erp.modules.riesgo.dto.HallazgoCatalogoDtos.HallazgoRequest;
import com.polygraph.erp.modules.riesgo.dto.HallazgoCatalogoDtos.HallazgoResponse;
import com.polygraph.erp.modules.riesgo.service.CatalogoHallazgosService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Catálogo de hallazgos. Leer: roles internos (el formulario muestra el nombre y los puntos de cada
 *  hallazgo); cambiar: solo ADMIN_POLYGRAPH. */
@RestController
@RequestMapping("/api/v1/catalogo/hallazgos")
@RequiredArgsConstructor
public class CatalogoHallazgosController {

    private final CatalogoHallazgosService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH', 'GESTOR', 'PROGRAMADOR', 'ANALISTA_INTERNO', 'POLIGRAFISTA', 'VISITADOR')")
    public List<HallazgoResponse> listar(@RequestParam(defaultValue = "false") boolean soloActivos) {
        return service.listar(soloActivos);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public HallazgoResponse crear(@Valid @RequestBody HallazgoRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public HallazgoResponse actualizar(@PathVariable Integer id, @Valid @RequestBody HallazgoRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/orden")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public List<HallazgoResponse> ordenar(@RequestBody List<Integer> ids) {
        return service.ordenar(ids);
    }
}
