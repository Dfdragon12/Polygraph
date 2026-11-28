package com.polygraph.erp.modules.busqueda.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.busqueda.dto.ResultadoBusquedaResponse;
import com.polygraph.erp.modules.busqueda.service.BusquedaGlobalService;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Buscador global (Ctrl+K) — ver BusquedaGlobalService para por qué solo estos dos roles y qué
 *  ve cada uno. */
@RestController
@RequestMapping("/api/v1/busqueda-global")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH', 'GESTOR', 'ANALISTA_INTERNO', 'POLIGRAFISTA', 'VISITADOR', 'PROGRAMADOR')")
public class BusquedaGlobalController {

    private final BusquedaGlobalService service;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<List<ResultadoBusquedaResponse>> buscar(@RequestParam String q, Authentication auth) {
        Usuario actor = usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(service.buscar(q, actor));
    }
}
