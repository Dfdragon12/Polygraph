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

/** Datos del estudio (N° de estudio, fecha de emisión, cliente, cargo, profesional) que una
 *  columna de plantilla puede mostrar sin que nadie los escriba — ver CampoServicio. */
@RestController
@RequestMapping("/api/v1/catalogo/campos-servicio")
@RequiredArgsConstructor
public class CampoServicioController {

    private final FormularioPlantillaService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<List<CampoCandidatoOpcion>> disponibles() {
        return ResponseEntity.ok(service.listarCamposServicioDisponibles());
    }
}
