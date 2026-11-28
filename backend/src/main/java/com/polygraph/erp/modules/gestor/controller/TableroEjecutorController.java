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

/** Tablero de analista, poligrafista y visitador: las novedades que cada uno registró en sus
 *  subprocesos y cómo va su respuesta (solo las suyas, igual que en el formulario). */
@RestController
@RequestMapping("/api/v1/ejecutor/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ANALISTA_INTERNO', 'POLIGRAFISTA', 'VISITADOR')")
public class TableroEjecutorController {

    private final NovedadesTableroService novedadesTableroService;
    private final UsuarioRepository usuarioRepository;

    @GetMapping("/novedades")
    public ResponseEntity<GraficasGestorResponse.Novedades> novedades(Authentication auth) {
        Long idUsuario = usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.UNAUTHORIZED))
                .getIdUsuario();
        return ResponseEntity.ok(novedadesTableroService.deEjecutor(idUsuario));
    }
}
