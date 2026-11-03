package com.polygraph.erp.modules.gestor.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.gestor.service.EnlaceProveedorService;
import com.polygraph.erp.modules.gestor.service.EnlaceProveedorService.Entrada;
import com.polygraph.erp.modules.gestor.service.EnlaceProveedorService.Generado;
import com.polygraph.erp.modules.gestor.service.EnlaceProveedorService.Sesion;
import com.polygraph.erp.security.EnlaceProveedorFilter;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

/** PF-09 · Enlace del proveedor externo. Lo maneja el programador responsable de la cita (o el administrador); el token se
 *  devuelve una sola vez al generarlo. {@code /proveedor/sesion} lo llama la pantalla del proveedor para saber que su enlace sirve. */
@RestController
@RequiredArgsConstructor
public class EnlaceProveedorController {

    public record SesionResponse(Long idServicioSubproceso, OffsetDateTime expiraEn) {}

    private final EnlaceProveedorService service;
    private final UsuarioRepository usuarioRepository;

    @PostMapping("/api/v1/gestor/asignaciones/{id}/enlace-proveedor")
    @PreAuthorize("hasAnyRole('PROGRAMADOR', 'ADMIN_POLYGRAPH')")
    public ResponseEntity<Generado> generar(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.generar(id, usuario(auth)));
    }

    @GetMapping("/api/v1/gestor/asignaciones/{id}/enlace-proveedor")
    @PreAuthorize("hasAnyRole('PROGRAMADOR', 'ADMIN_POLYGRAPH')")
    public ResponseEntity<List<Entrada>> historial(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(service.historial(id, usuario(auth)));
    }

    @DeleteMapping("/api/v1/gestor/asignaciones/{id}/enlace-proveedor")
    @PreAuthorize("hasAnyRole('PROGRAMADOR', 'ADMIN_POLYGRAPH')")
    public ResponseEntity<Void> revocar(@PathVariable Long id, Authentication auth) {
        service.revocar(id, usuario(auth));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/proveedor/sesion")
    public ResponseEntity<SesionResponse> sesion(HttpServletRequest request) {
        Object atributo = request.getAttribute(EnlaceProveedorFilter.ATRIBUTO);
        if (!(atributo instanceof Sesion s)) throw new ApiException("Solo con un enlace de proveedor.", HttpStatus.FORBIDDEN);
        return ResponseEntity.ok(new SesionResponse(s.idServicioSubproceso(), s.expiraEn()));
    }

    private Usuario usuario(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName()).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }
}
