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

/** Lista fija de campos del cliente (NIT, nombre de la empresa, teléfono) que se pueden mostrar
 *  en una plantilla — mismo mecanismo que CampoCandidatoController, para el cliente que solicitó
 *  el servicio en vez del candidato/evaluado. */
@RestController
@RequestMapping("/api/v1/catalogo/campos-cliente")
@RequiredArgsConstructor
public class CampoClienteController {

    private final FormularioPlantillaService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<List<CampoCandidatoOpcion>> disponibles() {
        return ResponseEntity.ok(service.listarCamposClienteDisponibles());
    }
}
