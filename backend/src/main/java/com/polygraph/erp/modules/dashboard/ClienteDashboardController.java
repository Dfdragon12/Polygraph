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
    private final com.polygraph.erp.modules.servicios.service.InicioClienteService inicioService;

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
            // uno o varios, separados por coma (ej. ENTREGADA_A_TIEMPO,ENTREGADA_TARDE)
            @RequestParam(required = false) java.util.List<com.polygraph.erp.shared.enums.EstadoTiempo> estadoTiempo,
            @RequestParam(required = false) String orden,
            // PB-05: los anillos de riesgo abren Mis solicitudes filtrada por nivel y periodo
            @RequestParam(required = false) com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo nivel,
            @RequestParam(required = false) String periodo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(servicio.listarSolicitudes(status, estadoTiempo, orden, nivel,
                periodo == null || periodo.isBlank() ? null : com.polygraph.erp.shared.estados.PeriodoEstadistica.de(periodo), PageRequest.of(page, size)));
    }

    /** PB-05: «Informes listos», «Requiere tu atención» y «Tiempos de entrega». {@code desde}: la última visita (marca lo nuevo). */
    @GetMapping("/inicio")
    public ResponseEntity<com.polygraph.erp.modules.servicios.service.InicioClienteService.Inicio> inicio(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime desde,
            org.springframework.security.core.Authentication auth) {
        return ResponseEntity.ok(inicioService.inicio(auth.getName(), desde));
    }

    /** PB-05: estadísticas de mi empresa por periodo (HOY, SEMANA, MES, ANIO, TOTAL). */
    @GetMapping("/estadisticas")
    public ResponseEntity<com.polygraph.erp.modules.servicios.service.EstadisticasEstudiosService.EstadisticasCliente> estadisticas(
            @RequestParam(defaultValue = "ANIO") String periodo, org.springframework.security.core.Authentication auth) {
        return ResponseEntity.ok(inicioService.estadisticas(auth.getName(), com.polygraph.erp.shared.estados.PeriodoEstadistica.de(periodo)));
    }

    @GetMapping("/gestor")
    public ResponseEntity<GestorContactoResponse> obtenerGestor() {
        return ResponseEntity.ok(servicio.obtenerGestor());
    }
}
