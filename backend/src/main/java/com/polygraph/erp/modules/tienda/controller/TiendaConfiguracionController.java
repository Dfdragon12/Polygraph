package com.polygraph.erp.modules.tienda.controller;

import com.polygraph.erp.modules.tienda.service.IvaService;
import com.polygraph.erp.modules.tienda.service.ParametrosTiendaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** Lo que el portal necesita saber de la tienda para decidir qué mostrar (IVA, planes, vigencia, cobertura). Solo lectura, sin datos sensibles. */
@RestController
@RequestMapping("/api/v1/tienda")
@RequiredArgsConstructor
public class TiendaConfiguracionController {

    private final IvaService ivaService;
    private final ParametrosTiendaService parametros;

    public record ConfiguracionTienda(boolean cobroConIva, BigDecimal ivaPorcentaje, boolean tiendaPorPlanes,
                                      boolean vigenciaPorPlan, boolean coberturaPorSede, boolean pospagoPlan100,
                                      boolean altaPospagoControlada, boolean moraAutomatica) {}

    @GetMapping("/configuracion")
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE', 'GESTOR', 'ADMIN_POLYGRAPH')")
    public ResponseEntity<ConfiguracionTienda> configuracion() {
        return ResponseEntity.ok(new ConfiguracionTienda(
                ivaService.activo(), ivaService.porcentaje(),
                parametros.activo(ParametrosTiendaService.TIENDA_POR_PLANES),
                parametros.activo(ParametrosTiendaService.VIGENCIA_POR_PLAN),
                parametros.activo(ParametrosTiendaService.COBERTURA_POR_SEDE),
                parametros.activo(ParametrosTiendaService.POSPAGO_PLAN_100),
                parametros.activo(ParametrosTiendaService.ALTA_POSPAGO_CONTROLADA),
                parametros.activo(ParametrosTiendaService.MORA_AUTOMATICA)));
    }
}
