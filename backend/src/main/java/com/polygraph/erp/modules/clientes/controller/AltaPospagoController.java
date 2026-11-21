package com.polygraph.erp.modules.clientes.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.clientes.service.AltaPospagoService;
import com.polygraph.erp.modules.clientes.service.AltaPospagoService.ExpedienteRequest;
import com.polygraph.erp.modules.clientes.service.AltaPospagoService.FilaReporte;
import com.polygraph.erp.modules.clientes.service.AltaPospagoService.SoportePospago;
import com.polygraph.erp.modules.clientes.service.AltaPospagoService.Verificacion;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Alta controlada del cliente pospago (TP-13): lista de verificación, soportes, aprobación y reporte de los pospago actuales.
 * Consultar: administrador y el gestor del cliente. Cambiar y aprobar: solo el administrador.
 */
@RestController
@RequestMapping("/api/v1/clientes")
@RequiredArgsConstructor
public class AltaPospagoController {

    private final AltaPospagoService service;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;

    public record AprobarRequest(boolean excepcion, String motivo) {}

    @GetMapping("/{id}/pospago/requisitos")
    @PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH', 'GESTOR')")
    public ResponseEntity<Verificacion> requisitos(@PathVariable Integer id, Authentication auth) {
        Usuario u = usuario(auth);
        if (u.getRol() == Rol.GESTOR) {
            var c = clienteRepository.findById(id).orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
            if (!Objects.equals(c.getIdGestor(), u.getIdUsuario())) throw new ApiException("Este cliente no es de tu cartera.", HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.ok(service.verificar(id));
    }

    @PutMapping("/{id}/pospago/expediente")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<Verificacion> guardar(@PathVariable Integer id, @RequestBody ExpedienteRequest req, Authentication auth) {
        return ResponseEntity.ok(service.guardarExpediente(id, req, auth.getName()));
    }

    @PostMapping(value = "/{id}/pospago/soportes/{tipo}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<Verificacion> subir(@PathVariable Integer id, @PathVariable SoportePospago tipo, @RequestPart("archivo") MultipartFile archivo,
                                              Authentication auth) {
        return ResponseEntity.ok(service.subirSoporte(id, tipo, archivo, auth.getName()));
    }

    @GetMapping("/{id}/pospago/soportes/{tipo}")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<Resource> descargar(@PathVariable Integer id, @PathVariable SoportePospago tipo) {
        var d = service.descargar(id, tipo);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.tipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.nombre(), StandardCharsets.UTF_8).build().toString())
                .body(d.recurso());
    }

    @PostMapping("/{id}/pospago/aprobar")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<Verificacion> aprobar(@PathVariable Integer id, @RequestBody AprobarRequest req, Authentication auth) {
        Usuario u = usuario(auth);
        return ResponseEntity.ok(service.aprobar(id, req.excepcion(), req.motivo(), auth.getName(), u.getRol()));
    }

    /** Los pospago actuales y lo que les falta de la lista. No quita ningún cupo. */
    @GetMapping("/pospago/reporte")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<List<FilaReporte>> reporte() {
        return ResponseEntity.ok(service.reporte());
    }

    private Usuario usuario(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName()).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }
}
