package com.polygraph.erp.modules.catalogo.controller;

import com.polygraph.erp.modules.catalogo.dto.EstadoSubprocesoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Los siete estados de un subproceso con su nombre, color, orden y texto para el cliente. Las pantallas lo leen de aquí. */
@RestController
@RequestMapping("/api/v1/catalogo/estados-subproceso")
public class EstadoSubprocesoController {

    @GetMapping
    public ResponseEntity<List<EstadoSubprocesoResponse>> listar() {
        return ResponseEntity.ok(EstadoSubprocesoResponse.catalogo());
    }
}
