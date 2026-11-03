package com.polygraph.erp.modules.formularios.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.formularios.service.FotoEvaluadoService;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Foto del evaluado de la poligrafía (PF-06): la toma el poligrafista en vivo, queda fija al pasar a la fase 2 y solo el gestor la reemplaza. */
@RestController
@RequestMapping("/api/v1/gestor/formularios/subproceso/{id}/foto")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('POLIGRAFISTA', 'PROGRAMADOR', 'GESTOR', 'ADMIN_POLYGRAPH')")
public class FotoEvaluadoController {

    private final FotoEvaluadoService service;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<FotoEvaluadoService.Estado> estado(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(service.estado(id, usuario(auth)));
    }

    @GetMapping("/imagen")
    public ResponseEntity<Resource> imagen(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).body(service.imagen(id, usuario(auth)));
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('POLIGRAFISTA')")
    public ResponseEntity<FotoEvaluadoService.Estado> tomar(@PathVariable Long id, @RequestParam MultipartFile foto,
                                                           @RequestParam boolean cedulaVerificada, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.tomar(id, foto, cedulaVerificada, usuario(auth)));
    }

    @PostMapping(value = "/reemplazo", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMIN_POLYGRAPH')")
    public ResponseEntity<FotoEvaluadoService.Estado> reemplazar(@PathVariable Long id, @RequestParam MultipartFile foto,
                                                                @RequestParam String motivo, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reemplazar(id, foto, motivo, usuario(auth)));
    }

    /** Se llama al pasar a la fase 2: desde ahí la foto queda fija. */
    @PostMapping("/bloquear")
    @PreAuthorize("hasRole('POLIGRAFISTA')")
    public ResponseEntity<FotoEvaluadoService.Estado> bloquear(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(service.bloquear(id, usuario(auth)));
    }

    private Usuario usuario(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName()).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }
}
