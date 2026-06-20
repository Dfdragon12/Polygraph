package com.polygraph.erp.modules.gestor.controller;

import com.polygraph.erp.modules.gestor.dto.GestorDashboardResponse;
import com.polygraph.erp.modules.gestor.service.GestorService;
import com.polygraph.erp.modules.solicitudes.dto.CambioEstadoRequest;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudDetalleResponse;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudResponse;
import com.polygraph.erp.modules.solicitudes.service.SolicitudService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

    @GetMapping("/dashboard")
    public ResponseEntity<GestorDashboardResponse> dashboard() {
        return ResponseEntity.ok(gestorService.obtenerDashboard());
    }

    @GetMapping("/solicitudes")
    public ResponseEntity<Page<SolicitudResponse>> listar(
            @RequestParam(required = false) String estado,
            @PageableDefault(size = 20, sort = "fechaSolicitud") Pageable pageable) {
        return ResponseEntity.ok(gestorService.listarSolicitudes(estado, pageable));
    }

    @GetMapping("/solicitudes/{id}")
    public ResponseEntity<SolicitudDetalleResponse> detalle(@PathVariable Long id) {
        return ResponseEntity.ok(solicitudService.obtenerDetalle(id));
    }

    @PatchMapping("/solicitudes/{id}/estado")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody CambioEstadoRequest request,
            Authentication auth) {
        solicitudService.cambiarEstado(id, request, (UserDetails) auth.getPrincipal());
        return ResponseEntity.noContent().build();
    }
}
