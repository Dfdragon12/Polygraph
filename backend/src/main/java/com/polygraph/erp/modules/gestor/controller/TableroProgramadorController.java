package com.polygraph.erp.modules.gestor.controller;

import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.gestor.dto.GraficasGestorResponse;
import com.polygraph.erp.modules.gestor.service.NovedadesTableroService;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Datos del tablero del programador que no salen de /gestor/asignaciones: las novedades de los
 *  subprocesos que él coordina (poligrafía/visita), que también le toca resolver. */
@RestController
@RequestMapping("/api/v1/programador/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PROGRAMADOR')")
public class TableroProgramadorController {

    private final NovedadesTableroService novedadesTableroService;
    private final UsuarioRepository usuarioRepository;

    @GetMapping("/novedades")
    public ResponseEntity<GraficasGestorResponse.Novedades> novedades(Authentication auth) {
        Long idProgramador = usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.UNAUTHORIZED))
                .getIdUsuario();
        return ResponseEntity.ok(novedadesTableroService.deProgramador(idProgramador));
    }
}
