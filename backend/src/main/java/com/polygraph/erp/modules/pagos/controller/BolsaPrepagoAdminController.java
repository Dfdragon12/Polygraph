package com.polygraph.erp.modules.pagos.controller;

import com.polygraph.erp.modules.pagos.service.BolsaPrepagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Reporte de solo lectura (NS-01): dónde el saldo guardado no coincide con las compras aprobadas. */
@RestController
@RequestMapping("/api/v1/admin/bolsa-prepago")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class BolsaPrepagoAdminController {

    private final BolsaPrepagoService bolsa;

    @GetMapping("/diferencias")
    public ResponseEntity<List<BolsaPrepagoService.Diferencia>> diferencias() {
        return ResponseEntity.ok(bolsa.diferenciasConComprasAprobadas());
    }
}
