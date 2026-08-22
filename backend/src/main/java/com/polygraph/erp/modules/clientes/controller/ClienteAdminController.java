package com.polygraph.erp.modules.clientes.controller;

import com.polygraph.erp.modules.clientes.dto.ActualizarClienteAdminRequest;
import com.polygraph.erp.modules.clientes.dto.ActualizarPospagoRequest;
import com.polygraph.erp.modules.clientes.dto.ActualizarUsuarioClienteRequest;
import com.polygraph.erp.modules.clientes.dto.ClienteAdminResponse;
import com.polygraph.erp.modules.clientes.dto.CrearUsuarioClienteRequest;
import com.polygraph.erp.modules.clientes.service.ClienteAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @PatchMapping("/{id}/pospago")
    public ResponseEntity<ClienteAdminResponse> actualizarPospago(
            @PathVariable Integer id,
            @Valid @RequestBody ActualizarPospagoRequest request) {
        return ResponseEntity.ok(service.actualizarPospago(id, request));
    }

    @PostMapping("/{id}/usuarios")
    public ResponseEntity<ClienteAdminResponse> crearUsuario(
            @PathVariable Integer id,
            @Valid @RequestBody CrearUsuarioClienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearUsuario(id, request));
    }

    @PutMapping("/{id}/usuarios/{idUsuario}")
    public ResponseEntity<ClienteAdminResponse> actualizarUsuario(
            @PathVariable Integer id,
            @PathVariable Long idUsuario,
            @Valid @RequestBody ActualizarUsuarioClienteRequest request) {
        return ResponseEntity.ok(service.actualizarUsuario(id, idUsuario, request));
    }

    @PatchMapping("/{id}/usuarios/{idUsuario}/activar")
    public ResponseEntity<ClienteAdminResponse> activarUsuario(@PathVariable Integer id, @PathVariable Long idUsuario) {
        return ResponseEntity.ok(service.cambiarEstadoUsuario(id, idUsuario, true));
    }

    @PatchMapping("/{id}/usuarios/{idUsuario}/desactivar")
    public ResponseEntity<ClienteAdminResponse> desactivarUsuario(@PathVariable Integer id, @PathVariable Long idUsuario) {
        return ResponseEntity.ok(service.cambiarEstadoUsuario(id, idUsuario, false));
    }
}
