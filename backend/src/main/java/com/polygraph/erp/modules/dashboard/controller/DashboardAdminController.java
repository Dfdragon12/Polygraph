package com.polygraph.erp.modules.dashboard.controller;

import com.polygraph.erp.modules.dashboard.dto.DashboardAdminResponse;
import com.polygraph.erp.modules.dashboard.dto.ServiciosPorEstadoResponse;
import com.polygraph.erp.modules.dashboard.service.DashboardAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class DashboardAdminController {

    private final DashboardAdminService service;

    @GetMapping
    public ResponseEntity<DashboardAdminResponse> obtener() {
        return ResponseEntity.ok(service.obtenerDashboard());
    }

    @GetMapping("/servicios")
    public ResponseEntity<ServiciosPorEstadoResponse> serviciosPorEstado(
            @RequestParam(required = false) Integer idCliente) {
        return ResponseEntity.ok(service.serviciosPorEstado(idCliente));
    }
}
