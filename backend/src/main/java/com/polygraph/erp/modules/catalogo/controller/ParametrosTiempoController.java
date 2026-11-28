package com.polygraph.erp.modules.catalogo.controller;

import com.polygraph.erp.shared.service.ParametrosTiempoService;
import com.polygraph.erp.shared.service.ParametrosTiempoService.ParametroDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Parámetros del cálculo de tiempos (zona horaria, hora de corte, umbral de "por vencer", regla de reprogramación). Solo el administrador. */
@RestController
@RequestMapping("/api/v1/admin/parametros-tiempo")
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
@RequiredArgsConstructor
public class ParametrosTiempoController {

    private final ParametrosTiempoService servicio;

    @GetMapping
    public ResponseEntity<List<ParametroDto>> listar() {
        return ResponseEntity.ok(servicio.listar());
    }

    /** Body: { "valor": "..." }. Valida el valor (hora HH:mm, zona horaria válida, número entero, ORIGINAL | VIGENTE). */
    @PutMapping("/{clave}")
    public ResponseEntity<List<ParametroDto>> cambiar(@PathVariable String clave, @RequestBody Map<String, String> body, Authentication auth) {
        servicio.actualizar(clave, body.get("valor"), auth.getName());
        return ResponseEntity.ok(servicio.listar());
    }
}
