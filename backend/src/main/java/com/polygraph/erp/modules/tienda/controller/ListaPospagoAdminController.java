package com.polygraph.erp.modules.tienda.controller;

import com.polygraph.erp.modules.tienda.service.ListaPospagoAdminService;
import com.polygraph.erp.modules.tienda.service.ListaPospagoAdminService.ItemHistorial;
import com.polygraph.erp.modules.tienda.service.PrecioPospagoService.Fila;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/** LP-03: edición de la lista de precios pospago. Solo el administrador y solo con el interruptor «Lista de precios pospago» encendido (si no, 409). */
@RestController
@RequestMapping("/api/v1/admin/catalogo/pospago/lista")
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
@RequiredArgsConstructor
public class ListaPospagoAdminController {

    public record CambioPrecio(@NotNull String modo, BigDecimal precioManual, Boolean ofrecido) {}

    private final ListaPospagoAdminService service;

    @PutMapping("/{idProceso}")
    public ResponseEntity<Fila> actualizar(@PathVariable Integer idProceso, @RequestBody CambioPrecio cambio, Authentication auth) {
        return ResponseEntity.ok(service.actualizar(idProceso, cambio.modo(), cambio.precioManual(), cambio.ofrecido(), auth.getName()));
    }

    @PostMapping("/restablecer")
    public ResponseEntity<List<Fila>> restablecer(Authentication auth) {
        return ResponseEntity.ok(service.restablecerTodos(auth.getName()));
    }

    @GetMapping("/{idProceso}/historial")
    public ResponseEntity<List<ItemHistorial>> historial(@PathVariable Integer idProceso) {
        return ResponseEntity.ok(service.historial(idProceso));
    }
}
