package com.polygraph.erp.modules.riesgo.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.riesgo.dto.GuardarParametrosRiesgoRequest;
import com.polygraph.erp.modules.riesgo.dto.ParametrosRiesgoResponse;
import com.polygraph.erp.modules.riesgo.service.ParametrosRiesgoService;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Umbrales y pisos del motor de riesgo — admin → Calificación de riesgo → Parámetros. */
@RestController
@RequestMapping("/api/v1/catalogo/riesgo/parametros")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class ParametrosRiesgoController {

    private final ParametrosRiesgoService service;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<ParametrosRiesgoResponse> obtener() {
        return ResponseEntity.ok(service.obtener());
    }

    @PutMapping
    public ResponseEntity<ParametrosRiesgoResponse> guardar(@Valid @RequestBody GuardarParametrosRiesgoRequest req, Authentication auth) {
        Usuario actor = usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(service.guardar(req, actor));
    }
}
