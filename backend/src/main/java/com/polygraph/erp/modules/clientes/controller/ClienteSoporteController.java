package com.polygraph.erp.modules.clientes.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.dto.SoporteClienteResponse;
import com.polygraph.erp.modules.clientes.service.SoporteClienteService;
import com.polygraph.erp.shared.enums.TipoSoporte;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/client/soportes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_CLIENTE')")
public class ClienteSoporteController {

    private final SoporteClienteService soporteService;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<List<SoporteClienteResponse>> listar(Authentication auth) {
        return ResponseEntity.ok(soporteService.listarParaCliente(resolverIdCliente(auth)));
    }

    @PostMapping(value = "/{tipo}", consumes = "multipart/form-data")
    public ResponseEntity<SoporteClienteResponse> subir(
            @PathVariable TipoSoporte tipo,
            @RequestParam MultipartFile archivo,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(soporteService.subir(resolverIdCliente(auth), tipo, archivo, auth.getName()));
    }

    @GetMapping("/{id}/descargar")
    public ResponseEntity<Resource> descargar(@PathVariable Integer id, Authentication auth) {
        var descarga = soporteService.descargarParaCliente(id, resolverIdCliente(auth));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(descarga.tipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(descarga.nombreArchivo(), StandardCharsets.UTF_8)
                                .build().toString())
                .body(descarga.recurso());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id, Authentication auth) {
        soporteService.eliminarParaCliente(id, resolverIdCliente(auth));
        return ResponseEntity.noContent().build();
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
