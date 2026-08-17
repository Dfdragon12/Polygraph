package com.polygraph.erp.modules.pagos.controller;

import com.polygraph.erp.modules.pagos.dto.EstadoPagoResponse;
import com.polygraph.erp.modules.pagos.dto.IniciarPagoWompiRequest;
import com.polygraph.erp.modules.pagos.dto.IniciarPagoWompiResponse;
import com.polygraph.erp.modules.pagos.service.PagoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pagos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH', 'GESTOR', 'ADMIN_CLIENTE')")
public class PagoController {

    private final PagoService pagoService;

    @PostMapping("/iniciar")
    public ResponseEntity<IniciarPagoWompiResponse> iniciar(
            @Valid @RequestBody IniciarPagoWompiRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pagoService.iniciarPago(request, auth.getName()));
    }

    @GetMapping("/{referencia}")
    public ResponseEntity<EstadoPagoResponse> obtener(@PathVariable String referencia, Authentication auth) {
        return ResponseEntity.ok(pagoService.obtenerEstado(referencia, auth.getName()));
    }
}
