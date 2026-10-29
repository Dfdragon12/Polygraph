package com.polygraph.erp.modules.usuarios.controller;

import com.polygraph.erp.modules.usuarios.dto.ActualizarPerfilInternoRequest;
import com.polygraph.erp.modules.usuarios.dto.PerfilInternoResponse;
import com.polygraph.erp.modules.usuarios.service.PerfilInternoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Mi perfil" de analista, poligrafista, programador, visitador y gestor. Ningún endpoint recibe
 *  un id: siempre es el usuario autenticado, así nadie puede ver ni editar el perfil de otro. */
@RestController
@RequestMapping("/api/v1/perfil")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ANALISTA_INTERNO', 'POLIGRAFISTA', 'PROGRAMADOR', 'VISITADOR', 'GESTOR')")
public class PerfilInternoController {

    private final PerfilInternoService service;

    @GetMapping
    public ResponseEntity<PerfilInternoResponse> obtener(Authentication auth) {
        return ResponseEntity.ok(service.obtener(auth.getName()));
    }

    @PutMapping
    public ResponseEntity<PerfilInternoResponse> actualizar(@Valid @RequestBody ActualizarPerfilInternoRequest request, Authentication auth) {
        return ResponseEntity.ok(service.actualizar(auth.getName(), request));
    }
}
