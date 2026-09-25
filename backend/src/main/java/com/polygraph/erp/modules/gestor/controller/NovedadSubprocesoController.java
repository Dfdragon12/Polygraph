package com.polygraph.erp.modules.gestor.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.gestor.dto.AgregarSeguimientoRequest;
import com.polygraph.erp.modules.gestor.dto.NovedadSubprocesoResponse;
import com.polygraph.erp.modules.gestor.dto.NovedadesSubprocesoResponse;
import com.polygraph.erp.modules.gestor.dto.RegistrarNovedadRequest;
import com.polygraph.erp.modules.gestor.dto.ResolverNovedadRequest;
import com.polygraph.erp.modules.gestor.service.NovedadSubprocesoService;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Novedades de un subproceso. Reportar: solo quien lo tiene asignado (analista/poligrafista/
 * visitador). Resolver: gestor del cliente, programador responsable o admin. Las reglas finas
 * (quién puede qué sobre cuál subproceso) viven en NovedadSubprocesoService.
 */
@RestController
@RequestMapping("/api/v1/gestor/asignaciones/{idServicioSubproceso}/novedades")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH', 'GESTOR', 'PROGRAMADOR', 'ANALISTA_INTERNO', 'POLIGRAFISTA', 'VISITADOR')")
public class NovedadSubprocesoController {

    private final NovedadSubprocesoService service;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<NovedadesSubprocesoResponse> listar(@PathVariable Long idServicioSubproceso, Authentication auth) {
        return ResponseEntity.ok(service.listar(idServicioSubproceso, resolverUsuario(auth)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ANALISTA_INTERNO', 'POLIGRAFISTA', 'VISITADOR')")
    public ResponseEntity<NovedadSubprocesoResponse> registrar(
            @PathVariable Long idServicioSubproceso, @Valid @RequestBody RegistrarNovedadRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.registrar(idServicioSubproceso, request, resolverUsuario(auth)));
    }

    @PatchMapping("/{idNovedad}/resolver")
    @PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH', 'GESTOR', 'PROGRAMADOR')")
    public ResponseEntity<NovedadSubprocesoResponse> resolver(
            @PathVariable Long idServicioSubproceso, @PathVariable Long idNovedad,
            @Valid @RequestBody ResolverNovedadRequest request, Authentication auth) {
        return ResponseEntity.ok(service.resolver(idServicioSubproceso, idNovedad, request, resolverUsuario(auth)));
    }

    /** Observación adicional sobre una novedad abierta — quien la reportó o quien coordina. */
    @PostMapping("/{idNovedad}/seguimientos")
    public ResponseEntity<NovedadSubprocesoResponse> agregarSeguimiento(
            @PathVariable Long idServicioSubproceso, @PathVariable Long idNovedad,
            @Valid @RequestBody AgregarSeguimientoRequest request, Authentication auth) {
        return ResponseEntity.ok(service.agregarSeguimiento(idServicioSubproceso, idNovedad, request, resolverUsuario(auth)));
    }

    private Usuario resolverUsuario(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }
}
