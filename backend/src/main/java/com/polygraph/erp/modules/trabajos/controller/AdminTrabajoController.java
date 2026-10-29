package com.polygraph.erp.modules.trabajos.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.trabajos.dto.MetricasTrabajosResponse;
import com.polygraph.erp.modules.trabajos.dto.TrabajoAdminResponse;
import com.polygraph.erp.modules.trabajos.service.ColaTrabajosService;
import com.polygraph.erp.modules.trabajos.service.LimpiezaTrabajosJob;
import com.polygraph.erp.modules.trabajos.service.MetricasTrabajosService;
import com.polygraph.erp.shared.enums.EstadoTrabajo;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** Panel Admin → Trabajos en segundo plano: ver la cola, los fallidos (la DLQ) y reintentarlos. */
@RestController
@RequestMapping("/api/v1/admin/trabajos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class AdminTrabajoController {

    private final ColaTrabajosService cola;
    private final MetricasTrabajosService metricas;
    private final LimpiezaTrabajosJob limpieza;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<Page<TrabajoAdminResponse>> listar(
            @RequestParam(required = false) EstadoTrabajo estado,
            @RequestParam(required = false) TipoTrabajo tipo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        var pageable = PageRequest.of(Math.max(0, pagina), Math.min(Math.max(1, tamano), 100), Sort.by(Sort.Direction.DESC, "id"));
        return ResponseEntity.ok(cola.listar(estado, tipo, pageable));
    }

    @GetMapping("/metricas")
    public ResponseEntity<MetricasTrabajosResponse> metricas() {
        return ResponseEntity.ok(metricas.obtener());
    }

    /** Borra ya los trabajos completados (menos los de los últimos minutos, que pueden estar
     *  descargándose). No toca en cola, procesando ni fallidos. */
    @DeleteMapping("/completados")
    public ResponseEntity<Map<String, Integer>> borrarCompletados() {
        return ResponseEntity.ok(Map.of(
                "borrados", limpieza.borrarCompletadosAhora(),
                "minutosProtegidos", LimpiezaTrabajosJob.MINUTOS_PROTEGIDOS));
    }

    @PostMapping("/{id}/reintentar")
    public ResponseEntity<TrabajoAdminResponse> reintentar(@PathVariable UUID id, Authentication auth) {
        Usuario admin = usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(cola.reintentar(id, admin));
    }
}
