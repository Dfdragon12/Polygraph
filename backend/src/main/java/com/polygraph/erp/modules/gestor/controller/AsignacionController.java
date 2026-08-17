package com.polygraph.erp.modules.gestor.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.gestor.dto.AsignacionResponse;
import com.polygraph.erp.modules.gestor.dto.AsignarMasivoRequest;
import com.polygraph.erp.modules.gestor.dto.AsignarRequest;
import com.polygraph.erp.modules.gestor.dto.EmpleadoAsignableResponse;
import com.polygraph.erp.modules.gestor.service.AsignacionService;
import com.polygraph.erp.shared.enums.EstadoAsignacion;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * GESTOR ve/asigna todo, sin restricción. PROGRAMADOR también puede entrar aquí, pero solo para
 * los subprocesos de poligrafía/visita (rolResponsable POLIGRAFISTA o VISITADOR) — coordina su
 * agenda, aunque quien queda como asignado es el poligrafista/visitador puntual.
 */
@RestController
@RequestMapping("/api/v1/gestor/asignaciones")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GESTOR', 'PROGRAMADOR')")
public class AsignacionController {

    private static final Set<Rol> ROLES_PROGRAMADOR = Set.of(Rol.POLIGRAFISTA, Rol.VISITADOR);

    private final AsignacionService asignacionService;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<Page<AsignacionResponse>> listar(
            @RequestParam(required = false) EstadoAsignacion estado,
            @RequestParam(required = false) Integer idTipoProgreso,
            @RequestParam(required = false) Long idUsuarioAsignado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(required = false) Integer idServicio,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication auth) {
        Usuario actor = resolverUsuario(auth);
        return ResponseEntity.ok(asignacionService.listar(
                estado, idTipoProgreso, idUsuarioAsignado, desde, hasta, idServicio,
                idGestor(actor), rolesPermitidos(actor), pageable));
    }

    @GetMapping("/calendario")
    public ResponseEntity<List<AsignacionResponse>> calendario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            Authentication auth) {
        Usuario actor = resolverUsuario(auth);
        return ResponseEntity.ok(asignacionService.listarCalendario(desde, hasta, idGestor(actor), rolesPermitidos(actor)));
    }

    @GetMapping("/tipo-progreso/{idTipoProgreso}/empleados")
    public ResponseEntity<List<EmpleadoAsignableResponse>> empleadosDisponibles(
            @PathVariable Integer idTipoProgreso, Authentication auth) {
        return ResponseEntity.ok(asignacionService.empleadosDisponibles(idTipoProgreso, rolesPermitidos(resolverUsuario(auth))));
    }

    @PatchMapping("/{id}/asignar")
    public ResponseEntity<AsignacionResponse> asignar(
            @PathVariable Long id, @Valid @RequestBody AsignarRequest request, Authentication auth) {
        return ResponseEntity.ok(asignacionService.asignar(id, request, rolesPermitidos(resolverUsuario(auth))));
    }

    @PatchMapping("/bulk-asignar")
    public ResponseEntity<List<AsignacionResponse>> asignarMasivo(
            @Valid @RequestBody AsignarMasivoRequest request, Authentication auth) {
        Usuario actor = resolverUsuario(auth);
        return ResponseEntity.ok(asignacionService.asignarMasivo(request, idGestor(actor), rolesPermitidos(actor)));
    }

    @PatchMapping("/{id}/completar")
    public ResponseEntity<AsignacionResponse> completar(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(asignacionService.completar(id, rolesPermitidos(resolverUsuario(auth))));
    }

    @PatchMapping("/{id}/desasignar")
    public ResponseEntity<AsignacionResponse> desasignar(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(asignacionService.desasignar(id, rolesPermitidos(resolverUsuario(auth))));
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<AsignacionResponse> cancelar(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(asignacionService.cancelar(id, rolesPermitidos(resolverUsuario(auth))));
    }

    private Usuario resolverUsuario(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }

    /** El filtro "mis clientes" solo aplica a GESTOR — PROGRAMADOR no tiene cartera de clientes, se filtra por rol. */
    private Long idGestor(Usuario actor) {
        return actor.getRol() == Rol.GESTOR ? actor.getIdUsuario() : null;
    }

    private Set<Rol> rolesPermitidos(Usuario actor) {
        return actor.getRol() == Rol.PROGRAMADOR ? ROLES_PROGRAMADOR : null;
    }
}
