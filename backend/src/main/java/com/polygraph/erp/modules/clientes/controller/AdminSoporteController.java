package com.polygraph.erp.modules.clientes.controller;

import com.polygraph.erp.modules.clientes.dto.SoporteClienteGestorResponse;
import com.polygraph.erp.modules.clientes.dto.SoporteClienteResponse;
import com.polygraph.erp.modules.clientes.dto.ValidarSoporteRequest;
import com.polygraph.erp.modules.clientes.service.SoporteClienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Revisión de documentos legales del cliente — por ahora la hace ADMIN_POLYGRAPH directamente
 * desde /admin/clientes, en lugar del gestor.
 */
@RestController
@RequestMapping("/api/v1/admin/soportes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class AdminSoporteController {

    private final SoporteClienteService soporteService;

    @GetMapping("/cliente/{idCliente}")
    public ResponseEntity<List<SoporteClienteResponse>> listarPorCliente(@PathVariable Integer idCliente) {
        return ResponseEntity.ok(soporteService.listarParaCliente(idCliente));
    }

    @PatchMapping("/{id}/validar")
    public ResponseEntity<SoporteClienteGestorResponse> validar(
            @PathVariable Integer id,
            @Valid @RequestBody ValidarSoporteRequest request,
            Authentication auth) {
        return ResponseEntity.ok(soporteService.validar(
                id, null, true, auth.getName(), request.aprobado(), request.observaciones()));
    }

    @GetMapping("/{id}/descargar")
    public ResponseEntity<Resource> descargar(@PathVariable Integer id) {
        var descarga = soporteService.descargarParaRevisor(id, null, true);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(descarga.tipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(descarga.nombreArchivo(), StandardCharsets.UTF_8)
                                .build().toString())
                .body(descarga.recurso());
    }
}
