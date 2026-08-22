package com.polygraph.erp.modules.gestor.controller;

import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.gestor.dto.GestorDashboardResponse;
import com.polygraph.erp.modules.gestor.service.GestorService;
import com.polygraph.erp.modules.solicitudes.dto.CambioEstadoRequest;
import com.polygraph.erp.modules.solicitudes.dto.ReversionSolicitudResponse;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudDetalleResponse;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudGestorResponse;
import com.polygraph.erp.modules.solicitudes.service.ReversionSolicitudService;
import com.polygraph.erp.modules.solicitudes.service.SolicitudService;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/gestor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('GESTOR')")
public class GestorController {

    private final GestorService gestorService;
    private final SolicitudService solicitudService;
    private final ReversionSolicitudService reversionSolicitudService;
    private final UsuarioRepository usuarioRepository;

    @GetMapping("/dashboard")
    public ResponseEntity<GestorDashboardResponse> dashboard(Authentication auth) {
        return ResponseEntity.ok(gestorService.obtenerDashboard(resolverIdGestor(auth)));
    }

    @GetMapping("/solicitudes")
    public ResponseEntity<Page<SolicitudGestorResponse>> listar(
            @RequestParam(required = false) String estado,
            @PageableDefault(size = 20, sort = "fechaSolicitud") Pageable pageable,
            Authentication auth) {
        return ResponseEntity.ok(gestorService.listarSolicitudes(resolverIdGestor(auth), estado, pageable));
    }

    private Long resolverIdGestor(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND))
                .getIdUsuario();
    }

    @GetMapping("/solicitudes/{id}")
    public ResponseEntity<SolicitudDetalleResponse> detalle(@PathVariable Integer id) {
        return ResponseEntity.ok(solicitudService.obtenerDetalle(id));
    }

    @PatchMapping("/solicitudes/{id}/estado")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Integer id,
            @Valid @RequestBody CambioEstadoRequest request,
            Authentication auth) {
        solicitudService.cambiarEstado(id, request, (UserDetails) auth.getPrincipal());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/solicitudes/{id}/solicitar-reversion")
    public ResponseEntity<Void> solicitarReversion(
            @PathVariable Integer id,
            @Valid @RequestBody CambioEstadoRequest request,
            Authentication auth) {
        solicitudService.solicitarReversion(id, request, (UserDetails) auth.getPrincipal());
        return ResponseEntity.accepted().build();
    }

    /** Solo lectura: el gestor puede consultar el estado de las reversiones, no aprobarlas ni rechazarlas. */
    @GetMapping("/reversiones")
    public ResponseEntity<Page<ReversionSolicitudResponse>> listarReversiones(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(reversionSolicitudService.listar(estado, q, pageable));
    }
}
