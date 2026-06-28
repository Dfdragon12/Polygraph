package com.polygraph.erp.modules.clientes.controller;

import com.polygraph.erp.modules.clientes.dto.ActualizarClienteAdminRequest;
import com.polygraph.erp.modules.clientes.dto.ClienteAdminResponse;
import com.polygraph.erp.modules.clientes.service.ClienteAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clientes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class ClienteAdminController {

    private final ClienteAdminService service;

    @GetMapping
    public ResponseEntity<List<ClienteAdminResponse>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteAdminResponse> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteAdminResponse> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody ActualizarClienteAdminRequest request) {
        return ResponseEntity.ok(service.actualizar(id, request));
    }

    @PatchMapping("/{id}/activar")
    public ResponseEntity<ClienteAdminResponse> activar(@PathVariable Integer id) {
        return ResponseEntity.ok(service.cambiarEstado(id, true));
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<ClienteAdminResponse> desactivar(@PathVariable Integer id) {
        return ResponseEntity.ok(service.cambiarEstado(id, false));
    }
}
