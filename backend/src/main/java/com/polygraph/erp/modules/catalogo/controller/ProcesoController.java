package com.polygraph.erp.modules.catalogo.controller;

import com.polygraph.erp.modules.catalogo.dto.*;
import com.polygraph.erp.modules.catalogo.service.CalificacionProcesoService;
import com.polygraph.erp.modules.catalogo.service.ProcesoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalogo/procesos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class ProcesoController {

    private final ProcesoService service;
    private final CalificacionProcesoService calificacionProcesoService;

    @GetMapping
    public ResponseEntity<List<ProcesoResponse>> listar() {
        return ResponseEntity.ok(service.listarTodos());
    }

    @GetMapping("/activos")
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE', 'GESTOR', 'ADMIN_POLYGRAPH')")
    public ResponseEntity<List<ProcesoPublicoResponse>> listarActivos() {
        return ResponseEntity.ok(service.listarActivos());
    }

    @PostMapping
    public ResponseEntity<ProcesoResponse> crear(@Valid @RequestBody ProcesoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProcesoResponse> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody ProcesoRequest req) {
        return ResponseEntity.ok(service.actualizar(id, req));
    }

    @PatchMapping("/{id}/activar")
    public ResponseEntity<ProcesoResponse> activar(@PathVariable Integer id) {
        return ResponseEntity.ok(service.cambiarEstado(id, true));
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<ProcesoResponse> desactivar(@PathVariable Integer id) {
        return ResponseEntity.ok(service.cambiarEstado(id, false));
    }

    /** Pesos de riesgo por subproceso, obligatorios, cobertura mínima y criticidad por defecto. */
    @GetMapping("/{id}/calificacion")
    public ResponseEntity<CalificacionProcesoResponse> obtenerCalificacion(@PathVariable Integer id) {
        return ResponseEntity.ok(calificacionProcesoService.obtener(id));
    }

    @PutMapping("/{id}/calificacion")
    public ResponseEntity<CalificacionProcesoResponse> guardarCalificacion(
            @PathVariable Integer id, @Valid @RequestBody GuardarCalificacionProcesoRequest req) {
        return ResponseEntity.ok(calificacionProcesoService.guardar(id, req));
    }

    @GetMapping("/{id}/pasos")
    public ResponseEntity<List<PasoProcesoResponse>> obtenerPasos(@PathVariable Integer id) {
        return ResponseEntity.ok(service.obtenerPasos(id));
    }

    @GetMapping("/{id}/pasos-publico")
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE', 'GESTOR', 'ADMIN_POLYGRAPH')")
    public ResponseEntity<List<PasoProcesoPublicoResponse>> obtenerPasosPublicos(@PathVariable Integer id) {
        return ResponseEntity.ok(service.obtenerPasosPublicos(id));
    }

    @PostMapping("/{id}/pasos")
    public ResponseEntity<PasoProcesoResponse> asignarPaso(
            @PathVariable Integer id,
            @Valid @RequestBody PasoProcesoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.asignarPaso(id, req));
    }

    @PutMapping("/{id}/pasos/{pasoId}")
    public ResponseEntity<PasoProcesoResponse> actualizarPaso(
            @PathVariable Integer id,
            @PathVariable Integer pasoId,
            @Valid @RequestBody PasoProcesoRequest req) {
        return ResponseEntity.ok(service.actualizarPaso(pasoId, req));
    }

    @DeleteMapping("/{id}/pasos/{pasoId}")
    public ResponseEntity<Void> eliminarPaso(
            @PathVariable Integer id,
            @PathVariable Integer pasoId) {
        service.eliminarPaso(pasoId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/tramos")
    public ResponseEntity<TramoPrecioResponse> agregarTramo(
            @PathVariable Integer id,
            @Valid @RequestBody TramoPrecioRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.agregarTramo(id, req));
    }

    @PutMapping("/{id}/tramos/{tramoId}")
    public ResponseEntity<TramoPrecioResponse> actualizarTramo(
            @PathVariable Integer id,
            @PathVariable Integer tramoId,
            @Valid @RequestBody TramoPrecioRequest req) {
        return ResponseEntity.ok(service.actualizarTramo(tramoId, req));
    }

    @DeleteMapping("/{id}/tramos/{tramoId}")
    public ResponseEntity<Void> eliminarTramo(
            @PathVariable Integer id,
            @PathVariable Integer tramoId) {
        service.eliminarTramo(tramoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/precios-ciudad")
    public ResponseEntity<List<PrecioCiudadResponse>> obtenerPreciosCiudad(@PathVariable Integer id) {
        return ResponseEntity.ok(service.listarPreciosCiudad(id));
    }

    @PutMapping("/{id}/precios-ciudad")
    public ResponseEntity<List<PrecioCiudadResponse>> guardarPreciosCiudad(
            @PathVariable Integer id,
            @Valid @RequestBody List<PrecioCiudadRequest> items) {
        return ResponseEntity.ok(service.guardarPreciosCiudad(id, items));
    }

    // Documentos que el candidato debe subir para este proceso — el candidato mismo los recibe
    // embebidos en LinkValidacionResponse (ver EvaluadoService.validarLink), no por acá: esto es
    // solo para que el admin los configure.
    @GetMapping("/{id}/documentos")
    public ResponseEntity<List<DocumentoRequeridoResponse>> obtenerDocumentosRequeridos(@PathVariable Integer id) {
        return ResponseEntity.ok(service.obtenerDocumentosRequeridos(id));
    }

    @PostMapping("/{id}/documentos")
    public ResponseEntity<DocumentoRequeridoResponse> agregarDocumentoRequerido(
            @PathVariable Integer id, @Valid @RequestBody DocumentoRequeridoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.agregarDocumentoRequerido(id, req));
    }

    @PutMapping("/{id}/documentos/{docId}")
    public ResponseEntity<DocumentoRequeridoResponse> actualizarDocumentoRequerido(
            @PathVariable Integer id, @PathVariable Integer docId, @Valid @RequestBody DocumentoRequeridoRequest req) {
        return ResponseEntity.ok(service.actualizarDocumentoRequerido(docId, req));
    }

    @DeleteMapping("/{id}/documentos/{docId}")
    public ResponseEntity<Void> eliminarDocumentoRequerido(@PathVariable Integer id, @PathVariable Integer docId) {
        service.eliminarDocumentoRequerido(docId);
        return ResponseEntity.noContent().build();
    }
}
