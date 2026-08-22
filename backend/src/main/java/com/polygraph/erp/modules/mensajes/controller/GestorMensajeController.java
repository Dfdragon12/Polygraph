package com.polygraph.erp.modules.mensajes.controller;

import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.mensajes.dto.ConversacionResumenResponse;
import com.polygraph.erp.modules.mensajes.dto.EnviarMensajeRequest;
import com.polygraph.erp.modules.mensajes.dto.MensajeResponse;
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
@RequestMapping("/api/v1/gestor/mensajes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('GESTOR')")
public class GestorMensajeController {

    private final MensajeService mensajeService;
    private final UsuarioRepository usuarioRepository;

    @GetMapping("/conversaciones")
    public ResponseEntity<List<ConversacionResumenResponse>> misConversaciones(Authentication auth) {
        return ResponseEntity.ok(mensajeService.misConversaciones(resolverIdGestor(auth)));
    }

    @GetMapping
    public ResponseEntity<List<MensajeResponse>> listar(@RequestParam Integer idCliente, Authentication auth) {
        return ResponseEntity.ok(mensajeService.listarParaGestor(idCliente, resolverIdGestor(auth)));
    }

    @PostMapping
    public ResponseEntity<MensajeResponse> enviar(
            @RequestParam Integer idCliente,
            @Valid @RequestBody EnviarMensajeRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mensajeService.enviarComoGestor(idCliente, resolverIdGestor(auth), auth.getName(), request.mensaje()));
    }

    private Long resolverIdGestor(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND))
                .getIdUsuario();
    }
}
