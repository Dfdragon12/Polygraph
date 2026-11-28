package com.polygraph.erp.modules.formularios.controller;

import com.polygraph.erp.modules.formularios.service.EjemploAntecedentesService;
import com.polygraph.erp.modules.formularios.service.EjemploAntecedentesService.EjemploCargado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Carga el estudio de ejemplo de Antecedentes (datos ficticios del PDF de docs/antecedentes). */
@RestController
@RequestMapping("/api/v1/catalogo/ejemplos")
@RequiredArgsConstructor
public class EjemploAntecedentesController {

    private final EjemploAntecedentesService service;

    @PostMapping("/antecedentes")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public EjemploCargado cargar(Authentication auth) {
        return service.cargar(auth.getName());
    }
}
