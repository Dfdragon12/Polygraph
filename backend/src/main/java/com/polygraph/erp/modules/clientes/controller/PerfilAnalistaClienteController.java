package com.polygraph.erp.modules.clientes.controller;

import com.polygraph.erp.modules.clientes.dto.ActualizarMiPerfilAnalistaClienteRequest;
import com.polygraph.erp.modules.clientes.dto.MiPerfilAnalistaClienteResponse;
import com.polygraph.erp.modules.clientes.service.PerfilAnalistaClienteService;
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

/** "Mi perfil" del analista de un cliente. Ningún endpoint recibe un id: siempre es el usuario autenticado. */
@RestController
@RequestMapping("/api/v1/client/mi-perfil")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ANALISTA_CLIENTE')")
public class PerfilAnalistaClienteController {

    private final PerfilAnalistaClienteService service;

    @GetMapping
    public ResponseEntity<MiPerfilAnalistaClienteResponse> obtener(Authentication auth) {
        return ResponseEntity.ok(service.obtener(auth.getName()));
    }

    @PutMapping
    public ResponseEntity<MiPerfilAnalistaClienteResponse> actualizar(
            @Valid @RequestBody ActualizarMiPerfilAnalistaClienteRequest request, Authentication auth) {
        return ResponseEntity.ok(service.actualizar(auth.getName(), request));
    }
}
