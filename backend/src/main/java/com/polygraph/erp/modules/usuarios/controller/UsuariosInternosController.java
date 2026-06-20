package com.polygraph.erp.modules.usuarios.controller;

import com.polygraph.erp.modules.usuarios.dto.ActualizarUsuarioInternoRequest;
import com.polygraph.erp.modules.usuarios.dto.CrearUsuarioInternoRequest;
import com.polygraph.erp.modules.usuarios.dto.UsuarioClienteResponse;
import com.polygraph.erp.modules.usuarios.dto.UsuarioInternoResponse;
import com.polygraph.erp.modules.usuarios.service.UsuariosInternosService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/usuarios-internos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class UsuariosInternosController {

    private final UsuariosInternosService service;

    @GetMapping
    public ResponseEntity<List<UsuarioInternoResponse>> listar() {
        return ResponseEntity.ok(service.listarTodos());
    }

    @GetMapping("/clientes")
    public ResponseEntity<List<UsuarioClienteResponse>> listarClientes() {
        return ResponseEntity.ok(service.listarClientes());
    }

    @PostMapping
    public ResponseEntity<UsuarioInternoResponse> crear(@Valid @RequestBody CrearUsuarioInternoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioInternoResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarUsuarioInternoRequest request) {
        return ResponseEntity.ok(service.actualizar(id, request));
    }

    @PatchMapping("/{id}/activar")
    public ResponseEntity<UsuarioInternoResponse> activar(@PathVariable Long id) {
        return ResponseEntity.ok(service.cambiarEstado(id, true));
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<UsuarioInternoResponse> desactivar(@PathVariable Long id) {
        return ResponseEntity.ok(service.cambiarEstado(id, false));
    }
}
