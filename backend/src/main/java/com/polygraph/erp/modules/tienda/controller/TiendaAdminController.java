package com.polygraph.erp.modules.tienda.controller;

import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.*;
import com.polygraph.erp.modules.tienda.service.BeneficiosPlanService;
import com.polygraph.erp.modules.tienda.service.HistorialPreciosService;
import com.polygraph.erp.modules.tienda.service.ParametrosTiendaService;
import com.polygraph.erp.modules.tienda.service.PlanesAdminService;
import com.polygraph.erp.modules.tienda.service.PreciosPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Administración de la tienda: precios por plan, beneficios, planes globales, parámetros e interruptores (TP-04).
 * Solo el administrador (ADMIN_POLYGRAPH): un gestor no puede editar precios.
 */
@RestController
@RequestMapping("/api/v1/catalogo")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class TiendaAdminController {

    private final PreciosPlanService precios;
    private final BeneficiosPlanService beneficios;
    private final HistorialPreciosService historial;
    private final PlanesAdminService planes;
    private final ParametrosTiendaService parametros;
    private final com.polygraph.erp.modules.tienda.service.PrecioPospagoService precioPospago;

    // ── Precios por plan de un servicio ──

    @GetMapping("/procesos/{id}/precios-plan")
    public ResponseEntity<Analisis> obtenerPrecios(@PathVariable Integer id) {
        return ResponseEntity.ok(precios.obtener(id));
    }

    @PutMapping("/procesos/{id}/precios-plan")
    public ResponseEntity<Analisis> guardarPrecios(@PathVariable Integer id, @RequestBody GuardarPreciosRequest req) {
        precioPospago.validarPrecios(id, req.precios());   // TP-11: no se deja sin precio el plan de 100 de un servicio pospago automático
        return ResponseEntity.ok(precios.guardar(id, req.precios()));
    }

    /** Vista previa y margen en vivo: no guarda nada y sirve también para un servicio que todavía no existe. */
    @PostMapping("/tienda/simular-precios")
    public ResponseEntity<Analisis> simular(@RequestBody SimulacionRequest req) {
        return ResponseEntity.ok(precios.analizar(req.precios(), req.costoDirecto()));
    }

    @GetMapping("/procesos/{id}/historial-precios")
    public ResponseEntity<List<HistorialPrecioResponse>> historial(@PathVariable Integer id) {
        return ResponseEntity.ok(historial.listar(id));
    }

    // ── Beneficios por plan ──

    @GetMapping("/procesos/{id}/beneficios")
    public ResponseEntity<List<BeneficioResponse>> listarBeneficios(@PathVariable Integer id) {
        return ResponseEntity.ok(beneficios.listar(id));
    }

    @PutMapping("/procesos/{id}/beneficios")
    public ResponseEntity<List<BeneficioResponse>> guardarBeneficios(@PathVariable Integer id, @RequestBody GuardarBeneficiosRequest req) {
        return ResponseEntity.ok(beneficios.guardar(id, req.beneficios()));
    }

    // ── Planes globales ──

    @GetMapping("/tienda/planes")
    public ResponseEntity<List<PlanAdminResponse>> listarPlanes() {
        return ResponseEntity.ok(planes.listar());
    }

    @PutMapping("/tienda/planes")
    public ResponseEntity<List<PlanAdminResponse>> guardarPlanes(@RequestBody GuardarPlanesRequest req, Authentication auth) {
        precioPospago.validarPlanes(req.planes());   // TP-11: no se oculta el plan de 100 si el pospago automático depende de él
        return ResponseEntity.ok(planes.guardar(req.planes(), auth.getName()));
    }

    // ── Parámetros globales e interruptores ──

    @GetMapping("/tienda/parametros")
    public ResponseEntity<List<ParametroResponse>> listarParametros() {
        return ResponseEntity.ok(parametros.listar().stream()
                .map(p -> new ParametroResponse(p.getClave(), p.getValor(), p.getTipo(), p.getDescripcion(), p.getActualizadoPor(), p.getFechaActualizacion()))
                .toList());
    }

    @PutMapping("/tienda/parametros/{clave}")
    public ResponseEntity<Void> cambiarParametro(@PathVariable String clave, @RequestBody CambiarParametroRequest req, Authentication auth) {
        parametros.cambiar(clave, req.valor(), auth.getName());
        return ResponseEntity.noContent().build();
    }
}
