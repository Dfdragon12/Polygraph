package com.polygraph.erp.modules.catalogo.controller;

import com.polygraph.erp.modules.catalogo.dto.CiudadRequest;
import com.polygraph.erp.modules.catalogo.dto.CiudadResponse;
import com.polygraph.erp.modules.catalogo.service.CiudadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ciudades")
@RequiredArgsConstructor
public class CiudadController {

    private final CiudadService service;

    @GetMapping
    public ResponseEntity<List<CiudadResponse>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    // Público — el formulario del evaluado no tiene sesión y necesita poder agregar su ciudad
    // si no está en el catálogo.
    @PostMapping
    public ResponseEntity<CiudadResponse> crear(@Valid @RequestBody CiudadRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req));
    }
}
