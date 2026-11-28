package com.polygraph.erp.modules.pagos.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.pagos.service.VencimientoLotesService;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** TP-07: alerta de unidades por vencer (cliente) y reporte de ingreso por vencimiento (administrador). */
@RestController
@RequiredArgsConstructor
public class LoteVencimientoController {

    private final VencimientoLotesService servicio;
    private final UsuarioRepository usuarioRepository;
    private final com.polygraph.erp.modules.pagos.service.ReporteNacionalesService reporteNacionales;

    /** Lotes del cliente que vencen dentro de la ventana de alerta. Lista vacía con la vigencia apagada. */
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE')")
    @GetMapping("/api/v1/client/lotes-por-vencer")
    public ResponseEntity<List<VencimientoLotesService.LotePorVencer>> lotesPorVencer() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (usuario.getIdCliente() == null) throw new ApiException("Usuario no asociado a ningún cliente", HttpStatus.FORBIDDEN);
        return ResponseEntity.ok(servicio.lotesPorVencer(usuario.getIdCliente()));
    }

    /** TP-08: unidades del paquete nacional usadas en municipios secundarios, por mes y servicio (vigilar el margen del paquete nacional). */
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    @GetMapping("/api/v1/admin/tienda/nacionales-en-municipios")
    public ResponseEntity<List<com.polygraph.erp.modules.pagos.service.ReporteNacionalesService.Fila>> nacionalesEnMunicipios(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(reporteNacionales.nacionalesEnMunicipios(desde, hasta));
    }

    /** Unidades vencidas (y su valor) entre dos fechas, para finanzas. */
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    @GetMapping("/api/v1/admin/tienda/vencimientos")
    public ResponseEntity<List<VencimientoLotesService.FilaVencimiento>> reporte(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(servicio.reporte(desde, hasta));
    }
}
