package com.polygraph.erp.modules.formularios.controller;

import com.polygraph.erp.modules.formularios.dto.CampoCandidatoOpcion;
import com.polygraph.erp.modules.formularios.service.FormularioPlantillaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Lista fija de campos del candidato que se pueden mostrar en una plantilla — no depende de
 *  ningún tipo de subproceso en particular, por eso vive fuera de FormularioPlantillaController. */
@RestController
@RequestMapping("/api/v1/catalogo/campos-candidato")
@RequiredArgsConstructor
public class CampoCandidatoController {

    private final FormularioPlantillaService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<List<CampoCandidatoOpcion>> disponibles() {
        return ResponseEntity.ok(service.listarCamposCandidatoDisponibles());
    }
}
