package com.polygraph.erp.modules.riesgo.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.riesgo.dto.CalificacionRiesgoResponse;
import com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Riesgo de un caso — pestaña "Riesgo" del detalle de la solicitud. Ver: admin y el gestor del
 *  cliente; decidir y ajustar criticidad: solo el gestor del cliente (reglas en el servicio). */
@RestController
@RequestMapping("/api/v1/riesgo/servicios/{idServicio}")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH', 'GESTOR')")
public class CalificacionRiesgoController {

    private final CalificacionRiesgoService service;
    private final UsuarioRepository usuarioRepository;

    public record CriticidadRequest(String criticidad) {}

    /** nivel vacío = volver al nivel automático (recomendación del motor). */
    public record DecisionRequest(String nivel, String justificacion) {}

    @GetMapping
    public ResponseEntity<CalificacionRiesgoResponse> obtener(@PathVariable Integer idServicio, Authentication auth) {
        return ResponseEntity.ok(service.obtener(idServicio, actor(auth)));
    }

    /** criticidad null o vacía = volver a la del proceso. */
    @PutMapping("/criticidad")
    public ResponseEntity<CalificacionRiesgoResponse> cambiarCriticidad(@PathVariable Integer idServicio,
                                                                        @RequestBody CriticidadRequest req, Authentication auth) {
        return ResponseEntity.ok(service.cambiarCriticidad(idServicio, req.criticidad(), actor(auth)));
    }

    @PutMapping("/decision")
    public ResponseEntity<CalificacionRiesgoResponse> decidir(@PathVariable Integer idServicio,
                                                              @jakarta.validation.Valid @RequestBody DecisionRequest req, Authentication auth) {
        return ResponseEntity.ok(service.decidir(idServicio, req.nivel(), req.justificacion(), actor(auth)));
    }

    private Usuario actor(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }
}
