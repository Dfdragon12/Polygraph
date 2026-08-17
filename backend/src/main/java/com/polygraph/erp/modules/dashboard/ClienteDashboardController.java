package com.polygraph.erp.modules.dashboard;

import com.polygraph.erp.modules.pagos.dto.SaldoServicioResponse;
import com.polygraph.erp.modules.servicios.dto.DashboardClienteResponse;
import com.polygraph.erp.modules.servicios.dto.GestorContactoResponse;
import com.polygraph.erp.modules.servicios.dto.SolicitudResumenResponse;
import com.polygraph.erp.modules.servicios.service.DashboardClienteService;
import com.polygraph.erp.shared.enums.EstadoServicio;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/client")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE')")
public class ClienteDashboardController {

    private final DashboardClienteService servicio;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardClienteResponse> obtenerDashboard() {
        return ResponseEntity.ok(servicio.obtenerDashboard());
    }

    @GetMapping("/service-packages")
    public ResponseEntity<List<SaldoServicioResponse>> obtenerBolsaServicios() {
        return ResponseEntity.ok(servicio.obtenerBolsaServicios());
    }

    @GetMapping("/requests")
    public ResponseEntity<Page<SolicitudResumenResponse>> listarSolicitudes(
            @RequestParam(required = false) EstadoServicio status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(servicio.listarSolicitudes(status, PageRequest.of(page, size)));
    }

    @GetMapping("/gestor")
    public ResponseEntity<GestorContactoResponse> obtenerGestor() {
        return ResponseEntity.ok(servicio.obtenerGestor());
    }
}
