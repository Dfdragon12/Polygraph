package com.polygraph.erp.modules.mensajes.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.mensajes.dto.EnviarMensajeRequest;
import com.polygraph.erp.modules.mensajes.dto.MensajeResponse;
import com.polygraph.erp.modules.mensajes.dto.NoLeidosResponse;
import com.polygraph.erp.modules.mensajes.service.MensajeService;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/client/mensajes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE')")
public class ClienteMensajeController {

    private final MensajeService mensajeService;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<List<MensajeResponse>> listar(Authentication auth) {
        return ResponseEntity.ok(mensajeService.listarParaCliente(resolverIdCliente(auth)));
    }

    @GetMapping("/no-leidos")
    public ResponseEntity<NoLeidosResponse> noLeidos(Authentication auth) {
        return ResponseEntity.ok(mensajeService.noLeidosParaCliente(resolverIdCliente(auth)));
    }

    @PostMapping
    public ResponseEntity<MensajeResponse> enviar(@Valid @RequestBody EnviarMensajeRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mensajeService.enviarComoCliente(resolverIdCliente(auth), auth.getName(), request.mensaje()));
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
