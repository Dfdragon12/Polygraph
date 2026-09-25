package com.polygraph.erp.modules.notificaciones.controller;

import com.polygraph.erp.modules.notificaciones.dto.NotificacionResponse;
import com.polygraph.erp.modules.notificaciones.dto.PaginaNotificacionesResponse;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService service;

    @GetMapping
    public ResponseEntity<List<NotificacionResponse>> listar(Authentication auth) {
        return ResponseEntity.ok(service.listar(auth.getName()));
    }

    @GetMapping("/pagina")
    public ResponseEntity<PaginaNotificacionesResponse> listarPagina(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "15") int tamano,
            @RequestParam(defaultValue = "false") boolean soloNoLeidas,
            Authentication auth) {
        return ResponseEntity.ok(service.listarPagina(auth.getName(), pagina, tamano, soloNoLeidas));
    }

    /** Borra de una vez todas las notificaciones ya leídas del usuario autenticado. */
    @DeleteMapping("/leidas")
    public ResponseEntity<Map<String, Integer>> eliminarLeidas(Authentication auth) {
        return ResponseEntity.ok(Map.of("eliminadas", service.eliminarLeidas(auth.getName())));
    }

    @GetMapping("/no-leidas")
    public ResponseEntity<Map<String, Long>> contarNoLeidas(Authentication auth) {
        return ResponseEntity.ok(Map.of("total", service.contarNoLeidas(auth.getName())));
    }

    @PatchMapping("/leer-todas")
    public ResponseEntity<Void> marcarTodasLeidas(Authentication auth) {
        service.marcarTodasLeidas(auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/leer")
    public ResponseEntity<Void> marcarLeida(@PathVariable Long id, Authentication auth) {
        service.marcarLeida(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication auth) {
        service.eliminar(id, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
