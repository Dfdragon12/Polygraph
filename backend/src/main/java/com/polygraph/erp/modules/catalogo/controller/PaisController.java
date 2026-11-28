package com.polygraph.erp.modules.catalogo.controller;

import com.polygraph.erp.modules.catalogo.dto.PaisResponse;
import com.polygraph.erp.modules.catalogo.repository.PaisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogo de países — lista fija (sembrada por migración, no hardcodeada en código) para el
 *  selector de país de nacimiento/residencia de un candidato o de un cliente. Público: se usa
 *  desde formularios sin sesión (registro de candidato/cliente). */
@RestController
@RequestMapping("/api/v1/paises")
@RequiredArgsConstructor
public class PaisController {

    private final PaisRepository repository;

    @GetMapping
    public ResponseEntity<List<PaisResponse>> listar() {
        return ResponseEntity.ok(repository.findAllByOrderByNombreAsc().stream().map(PaisResponse::from).toList());
    }
}
