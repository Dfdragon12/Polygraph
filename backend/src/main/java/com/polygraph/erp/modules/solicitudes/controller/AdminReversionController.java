package com.polygraph.erp.modules.solicitudes.controller;

import com.polygraph.erp.modules.solicitudes.dto.ReversionSolicitudResponse;
import com.polygraph.erp.modules.solicitudes.dto.RevisionReversionRequest;
import com.polygraph.erp.modules.solicitudes.service.ReversionSolicitudService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/reversiones")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class AdminReversionController {

    private final ReversionSolicitudService reversionSolicitudService;

    @GetMapping
    public ResponseEntity<Page<ReversionSolicitudResponse>> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(reversionSolicitudService.listar(estado, q, pageable));
    }

    @GetMapping("/pendientes/conteo")
    public ResponseEntity<Map<String, Long>> contarPendientes() {
        return ResponseEntity.ok(Map.of("pendientes", reversionSolicitudService.contarPendientes()));
    }

    @PatchMapping("/{id}/aprobar")
    public ResponseEntity<Void> aprobar(
            @PathVariable Long id,
            @RequestBody(required = false) RevisionReversionRequest request,
            Authentication auth) {
        reversionSolicitudService.aprobar(id, request != null ? request : new RevisionReversionRequest(null),
                (UserDetails) auth.getPrincipal());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/rechazar")
    public ResponseEntity<Void> rechazar(
            @PathVariable Long id,
            @RequestBody(required = false) RevisionReversionRequest request,
            Authentication auth) {
        reversionSolicitudService.rechazar(id, request != null ? request : new RevisionReversionRequest(null),
                (UserDetails) auth.getPrincipal());
        return ResponseEntity.noContent().build();
    }
}
