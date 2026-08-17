package com.polygraph.erp.modules.gestor.controller;

import com.polygraph.erp.modules.gestor.dto.GenerarLinkRequest;
import com.polygraph.erp.modules.gestor.dto.LinkCandidatoResponse;
import com.polygraph.erp.modules.gestor.dto.LinkEstadisticasResponse;
import com.polygraph.erp.modules.gestor.dto.ResumenEvaluadoResponse;
import com.polygraph.erp.modules.gestor.service.LinkCandidatoGestorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/gestor/tokens")
@RequiredArgsConstructor
@PreAuthorize("hasRole('GESTOR')")
public class LinkCandidatoController {

    private final LinkCandidatoGestorService tokenService;

    @GetMapping("/estadisticas")
    public ResponseEntity<LinkEstadisticasResponse> estadisticas() {
        return ResponseEntity.ok(tokenService.estadisticas());
    }

    @GetMapping("/servicio/{idServicio}")
    public ResponseEntity<LinkCandidatoResponse> porServicio(@PathVariable Integer idServicio) {
        return tokenService.obtenerPorServicio(idServicio)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/servicio/{idServicio}/historial")
    public ResponseEntity<List<LinkCandidatoResponse>> historialPorServicio(@PathVariable Integer idServicio) {
        return ResponseEntity.ok(tokenService.listarHistorialPorServicio(idServicio));
    }

    @GetMapping("/servicio/{idServicio}/resumen")
    public ResponseEntity<ResumenEvaluadoResponse> resumenPorServicio(@PathVariable Integer idServicio) {
        return ResponseEntity.ok(tokenService.obtenerResumenPorServicio(idServicio));
    }

    @GetMapping("/servicio/{idServicio}/documentos/{idDocumento}/descargar")
    public ResponseEntity<Resource> descargarDocumento(@PathVariable Integer idServicio, @PathVariable Long idDocumento) {
        var descarga = tokenService.descargarDocumento(idServicio, idDocumento);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(descarga.tipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(descarga.nombreArchivo(), StandardCharsets.UTF_8)
                                .build().toString())
                .body(descarga.recurso());
    }

    @GetMapping
    public ResponseEntity<Page<LinkCandidatoResponse>> listar(
            @RequestParam(required = false) String estado,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(tokenService.listar(estado, pageable));
    }

    @PostMapping
    public ResponseEntity<LinkCandidatoResponse> generar(
            @Valid @RequestBody GenerarLinkRequest request,
            Authentication auth,
            HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tokenService.generar(request, auth.getName(), ip));
    }

    @PostMapping("/{id}/enviar-correo")
    public ResponseEntity<Void> enviarCorreo(@PathVariable Long id) {
        tokenService.enviarCorreo(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/revocar")
    public ResponseEntity<Void> revocar(@PathVariable Long id) {
        tokenService.revocar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/revertir")
    public ResponseEntity<LinkCandidatoResponse> revertir(@PathVariable Long id) {
        return ResponseEntity.ok(tokenService.revertir(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        tokenService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
