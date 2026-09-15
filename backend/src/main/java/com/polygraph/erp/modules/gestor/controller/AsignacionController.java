package com.polygraph.erp.modules.gestor.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.gestor.dto.AsignacionResponse;
import com.polygraph.erp.modules.gestor.dto.AsignarMasivoRequest;
import com.polygraph.erp.modules.gestor.dto.AsignarRequest;
import com.polygraph.erp.modules.gestor.dto.ConfirmarAsistenciaRequest;
import com.polygraph.erp.modules.gestor.dto.ConteoAsignacionesResponse;
import com.polygraph.erp.modules.gestor.dto.EmpleadoAsignableResponse;
import com.polygraph.erp.modules.gestor.dto.FranjaHorariaResponse;
import com.polygraph.erp.modules.gestor.dto.HistorialAsignacionResponse;
import com.polygraph.erp.modules.gestor.dto.HistorialLlamadaResponse;
import com.polygraph.erp.modules.gestor.dto.AsignarProgramadorRequest;
import com.polygraph.erp.modules.gestor.dto.ProgramadorOpcionResponse;
import com.polygraph.erp.modules.gestor.dto.SubprocesoOpcionResponse;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * GESTOR solo ve/asigna subprocesos de ANALISTA_INTERNO — poligrafía y visita domiciliaria NO las
 * asigna el gestor: el PROGRAMADOR recibe la notificación y las coordina desde su propio módulo.
 * PROGRAMADOR, a su vez, solo ve/asigna subprocesos de poligrafía/visita (rolResponsable
 * POLIGRAFISTA o VISITADOR), aunque quien queda como asignado es el poligrafista/visitador puntual.
 * ANALISTA_INTERNO solo ve y completa SUS PROPIOS subprocesos ya asignados — no asigna ni
 * reprograma (eso lo sigue haciendo GESTOR), por eso solo tiene acceso a listar/conteo/completar/
 * historial, con @PreAuthorize puntual en cada uno de esos — el resto de endpoints se queda
 * restringido a GESTOR/PROGRAMADOR con su propio @PreAuthorize de método.
 */
@RestController
@RequestMapping("/api/v1/gestor/asignaciones")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GESTOR', 'PROGRAMADOR', 'ANALISTA_INTERNO', 'POLIGRAFISTA', 'VISITADOR')")
public class AsignacionController {

    private static final Set<Rol> ROLES_PROGRAMADOR = Set.of(Rol.POLIGRAFISTA, Rol.VISITADOR);
    private static final Set<Rol> ROLES_GESTOR = Set.of(Rol.ANALISTA_INTERNO);
    private static final Set<Rol> ROLES_ANALISTA = Set.of(Rol.ANALISTA_INTERNO);
    /** POLIGRAFISTA/VISITADOR, igual que ANALISTA_INTERNO, solo ven y completan SUS PROPIOS
     *  subprocesos ya asignados por PROGRAMADOR — no asignan ni reprograman. */
    private static final Set<Rol> ROLES_AUTOGESTION = Set.of(Rol.ANALISTA_INTERNO, Rol.POLIGRAFISTA, Rol.VISITADOR);

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
        // Al consultar los subprocesos de UN servicio puntual (vista "Solicitudes" de Gestor), se
        // le muestran también los de poligrafía/visita de sus propios clientes — en solo lectura,
        // no puede asignarlos (eso lo sigue bloqueando AsignacionService en asignar/desasignar/etc.).
        // Sin esto, el gestor veía "sin subprocesos" en servicios que sí tenían, solo que de otro rol.
        // El idGestor("mis clientes") sigue aplicando siempre, así que no ve servicios ajenos.
        // El listado general (para trabajar/asignar, sin idServicio) se mantiene acotado a su alcance.
        boolean verSinRestriccionDeRol = idServicio != null && actor.getRol() == Rol.GESTOR;
        Set<Rol> roles = verSinRestriccionDeRol ? null : rolesPermitidos(actor);
        return ResponseEntity.ok(asignacionService.listar(
                estado, idTipoProgreso, idUsuarioAsignadoForzado(actor, idUsuarioAsignado), desde, hasta, idServicio,
                idGestor(actor), roles, pageable, idProgramadorResponsableForzado(actor)));
    }

    @GetMapping("/calendario")
    @PreAuthorize("hasAnyRole('GESTOR', 'PROGRAMADOR')")
    public ResponseEntity<List<AsignacionResponse>> calendario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            Authentication auth) {
        Usuario actor = resolverUsuario(auth);
        return ResponseEntity.ok(asignacionService.listarCalendario(
                desde, hasta, idGestor(actor), rolesPermitidos(actor), idProgramadorResponsableForzado(actor)));
    }

    @GetMapping("/conteo")
    public ResponseEntity<ConteoAsignacionesResponse> conteo(Authentication auth) {
        Usuario actor = resolverUsuario(auth);
        return ResponseEntity.ok(asignacionService.contarPorEstado(
                idGestor(actor), idUsuarioAsignadoForzado(actor, null), rolesPermitidos(actor),
                idProgramadorResponsableForzado(actor)));
    }

    @GetMapping("/tipo-progreso/{idTipoProgreso}/empleados")
    @PreAuthorize("hasAnyRole('GESTOR', 'PROGRAMADOR')")
    public ResponseEntity<List<EmpleadoAsignableResponse>> empleadosDisponibles(
            @PathVariable Integer idTipoProgreso,
            @RequestParam(required = false) Integer idCiudad,
            Authentication auth) {
        return ResponseEntity.ok(asignacionService.empleadosDisponibles(idTipoProgreso, idCiudad, rolesPermitidos(resolverUsuario(auth))));
    }

    /** @param idTipoProgreso si se indica, el tamaño de franja por defecto es el estimado de ese
     *  subproceso; si no viene, es una consulta general de disponibilidad del empleado (pestaña
     *  "Disponibilidad"), sin acotar a un subproceso puntual.
     *  @param duracionMinutos duración a usar en vez del estimado del catálogo — si el gestor/
     *  programador la ajustó en el formulario, o el tamaño de bloque elegido en "Disponibilidad".
     *  @param idExcluir al reprogramar una asignación existente, su propio id — para que su franja
     *  actual no aparezca ocupada por sí misma. */
    @GetMapping("/franjas-disponibles")
    @PreAuthorize("hasAnyRole('GESTOR', 'PROGRAMADOR')")
    public ResponseEntity<List<FranjaHorariaResponse>> franjasDisponibles(
            @RequestParam Long idUsuarioAsignado,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Integer idTipoProgreso,
            @RequestParam(required = false) Integer duracionMinutos,
            @RequestParam(required = false) Long idExcluir,
            Authentication auth) {
        return ResponseEntity.ok(asignacionService.franjasDisponibles(
                idUsuarioAsignado, fecha, idTipoProgreso, duracionMinutos, idExcluir, rolesPermitidos(resolverUsuario(auth))));
    }

    @PatchMapping("/{id}/asignar")
    @PreAuthorize("hasAnyRole('GESTOR', 'PROGRAMADOR')")
    public ResponseEntity<AsignacionResponse> asignar(
            @PathVariable Long id, @Valid @RequestBody AsignarRequest request, Authentication auth) {
        Usuario actor = resolverUsuario(auth);
        return ResponseEntity.ok(asignacionService.asignar(id, request, actor, rolesPermitidos(actor)));
    }

    @PatchMapping("/bulk-asignar")
    @PreAuthorize("hasAnyRole('GESTOR', 'PROGRAMADOR')")
    public ResponseEntity<List<AsignacionResponse>> asignarMasivo(
            @Valid @RequestBody AsignarMasivoRequest request, Authentication auth) {
        Usuario actor = resolverUsuario(auth);
        return ResponseEntity.ok(asignacionService.asignarMasivo(request, actor, idGestor(actor), rolesPermitidos(actor)));
    }

    @PatchMapping("/{id}/completar")
    public ResponseEntity<AsignacionResponse> completar(@PathVariable Long id, Authentication auth) {
        Usuario actor = resolverUsuario(auth);
        return ResponseEntity.ok(asignacionService.completar(id, actor, rolesPermitidos(actor)));
    }

    @PatchMapping("/{id}/desasignar")
    @PreAuthorize("hasAnyRole('GESTOR', 'PROGRAMADOR')")
    public ResponseEntity<AsignacionResponse> desasignar(@PathVariable Long id, Authentication auth) {
        Usuario actor = resolverUsuario(auth);
        return ResponseEntity.ok(asignacionService.desasignar(id, actor, rolesPermitidos(actor)));
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('GESTOR', 'PROGRAMADOR')")
    public ResponseEntity<AsignacionResponse> cancelar(@PathVariable Long id, Authentication auth) {
        Usuario actor = resolverUsuario(auth);
        return ResponseEntity.ok(asignacionService.cancelar(id, actor, rolesPermitidos(actor)));
    }

    @PatchMapping("/{id}/confirmacion")
    @PreAuthorize("hasAnyRole('GESTOR', 'PROGRAMADOR')")
    public ResponseEntity<AsignacionResponse> confirmarAsistencia(
            @PathVariable Long id, @Valid @RequestBody ConfirmarAsistenciaRequest request, Authentication auth) {
        Usuario actor = resolverUsuario(auth);
        return ResponseEntity.ok(asignacionService.confirmarAsistencia(id, request, actor, rolesPermitidos(actor)));
    }

    /** El gestor elige a qué PROGRAMADOR le encarga coordinar este subproceso de poligrafía/
     *  visita — que solo ve en solo lectura — y le llega la notificación solo a esa persona. */
    @PatchMapping("/{id}/asignar-programador")
    @PreAuthorize("hasAnyRole('GESTOR', 'PROGRAMADOR')")
    public ResponseEntity<AsignacionResponse> asignarProgramador(
            @PathVariable Long id, @Valid @RequestBody AsignarProgramadorRequest request, Authentication auth) {
        return ResponseEntity.ok(asignacionService.asignarProgramador(id, request.idProgramador(), resolverUsuario(auth)));
    }

    /** Opciones para el selector "Asignar programador". */
    @GetMapping("/programadores")
    @PreAuthorize("hasAnyRole('GESTOR', 'PROGRAMADOR')")
    public ResponseEntity<List<ProgramadorOpcionResponse>> programadores() {
        return ResponseEntity.ok(asignacionService.listarProgramadoresActivos());
    }

    @GetMapping("/{id}/historial")
    public ResponseEntity<List<HistorialAsignacionResponse>> historial(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(asignacionService.historial(id, rolesPermitidos(resolverUsuario(auth))));
    }

    @GetMapping("/{id}/historial-llamadas")
    @PreAuthorize("hasAnyRole('GESTOR', 'PROGRAMADOR')")
    public ResponseEntity<List<HistorialLlamadaResponse>> historialLlamadas(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(asignacionService.historialLlamadas(id, rolesPermitidos(resolverUsuario(auth))));
    }

    /** Opciones para el filtro "Subproceso" — acotadas al alcance del rol (no el catálogo completo). */
    @GetMapping("/subprocesos")
    @PreAuthorize("hasAnyRole('GESTOR', 'PROGRAMADOR')")
    public ResponseEntity<List<SubprocesoOpcionResponse>> subprocesos(Authentication auth) {
        return ResponseEntity.ok(asignacionService.listarSubprocesosPermitidos(rolesPermitidos(resolverUsuario(auth))));
    }

    private Usuario resolverUsuario(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }

    /** El filtro "mis clientes" solo aplica a GESTOR — PROGRAMADOR no tiene cartera de clientes, se filtra por rol. */
    private Long idGestor(Usuario actor) {
        return actor.getRol() == Rol.GESTOR ? actor.getIdUsuario() : null;
    }

    /** ANALISTA_INTERNO/POLIGRAFISTA/VISITADOR no eligen a quién ver — siempre son sus propios
     *  subprocesos asignados, sin importar qué idUsuarioAsignado venga en el query param (si
     *  viniera otro, lo ignora). */
    private Long idUsuarioAsignadoForzado(Usuario actor, Long idUsuarioAsignadoParam) {
        return ROLES_AUTOGESTION.contains(actor.getRol()) ? actor.getIdUsuario() : idUsuarioAsignadoParam;
    }

    /** PROGRAMADOR solo ve los subprocesos de poligrafía/visita que el GESTOR le encargó a él
     *  puntualmente (ServicioSubproceso.programadorResponsable) — no el resto de la cola general,
     *  aunque siga estando en su alcance de rol. Sin excepciones ni parámetro para desactivarlo. */
    private Long idProgramadorResponsableForzado(Usuario actor) {
        return actor.getRol() == Rol.PROGRAMADOR ? actor.getIdUsuario() : null;
    }

    private Set<Rol> rolesPermitidos(Usuario actor) {
        if (actor.getRol() == Rol.PROGRAMADOR) return ROLES_PROGRAMADOR;
        if (actor.getRol() == Rol.GESTOR) return ROLES_GESTOR;
        if (actor.getRol() == Rol.ANALISTA_INTERNO) return ROLES_ANALISTA;
        // POLIGRAFISTA/VISITADOR solo ven subprocesos de su propio rol — igual de acotado que
        // ANALISTA_INTERNO con el suyo.
        if (actor.getRol() == Rol.POLIGRAFISTA) return Set.of(Rol.POLIGRAFISTA);
        if (actor.getRol() == Rol.VISITADOR) return Set.of(Rol.VISITADOR);
        return null;
    }
}
