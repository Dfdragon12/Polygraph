package com.polygraph.erp.modules.clientes.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.dto.ActualizarClientePerfilRequest;
import com.polygraph.erp.modules.clientes.dto.ClientePerfilResponse;
import com.polygraph.erp.modules.clientes.service.ClienteAdminService;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/client/perfil")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_CLIENTE')")
public class ClientePerfilController {

    private final ClienteAdminService clienteAdminService;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<ClientePerfilResponse> obtener(Authentication auth) {
        return ResponseEntity.ok(clienteAdminService.obtenerPerfilPropio(resolverIdCliente(auth)));
    }

    @PutMapping
    public ResponseEntity<ClientePerfilResponse> actualizar(
            @Valid @RequestBody ActualizarClientePerfilRequest request, Authentication auth) {
        return ResponseEntity.ok(clienteAdminService.actualizarPerfilPropio(resolverIdCliente(auth), request));
    }

    private Integer resolverIdCliente(Authentication auth) {
        Usuario usuario = usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (usuario.getIdCliente() == null) {
            throw new ApiException("Usuario no asociado a ningún cliente", HttpStatus.FORBIDDEN);
        }
        return usuario.getIdCliente();
    }
}
