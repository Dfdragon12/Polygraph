package com.polygraph.erp.modules.gestor.controller;

import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.dto.ClienteAdminResponse;
import com.polygraph.erp.modules.clientes.dto.CrearUsuarioPropioRequest;
import com.polygraph.erp.modules.clientes.service.ClienteAdminService;
import com.polygraph.erp.modules.gestor.dto.ConteoEstadosResponse;
import com.polygraph.erp.modules.gestor.dto.GestorDashboardResponse;
import com.polygraph.erp.modules.gestor.service.BandejaGestorService;
import com.polygraph.erp.modules.gestor.service.GestorService;
import com.polygraph.erp.modules.solicitudes.dto.CambioEstadoRequest;
import com.polygraph.erp.modules.solicitudes.dto.CorregirCiudadRequest;
import com.polygraph.erp.modules.solicitudes.dto.ReversionSolicitudResponse;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudDetalleResponse;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudGestorResponse;
import com.polygraph.erp.modules.solicitudes.service.ReversionSolicitudService;
import com.polygraph.erp.modules.solicitudes.service.SolicitudService;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/gestor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('GESTOR')")
public class GestorController {

    private final GestorService gestorService;
    private final BandejaGestorService bandejaService;
    private final SolicitudService solicitudService;
    private final com.polygraph.erp.modules.solicitudes.service.CancelacionSolicitudService cancelacionSolicitudService;
    private final ReversionSolicitudService reversionSolicitudService;
    private final UsuarioRepository usuarioRepository;
    private final ClienteAdminService clienteAdminService;

    @GetMapping("/dashboard")
    public ResponseEntity<GestorDashboardResponse> dashboard(Authentication auth) {
        return ResponseEntity.ok(gestorService.obtenerDashboard(resolverIdGestor(auth)));
    }

    /** Conteos agrupados para las gráficas del tablero (solicitudes por semana, entregas,
     *  carga por cliente y por estudio) — solo de sus clientes. */
    @GetMapping("/dashboard/graficas")
    public ResponseEntity<com.polygraph.erp.modules.gestor.dto.GraficasGestorResponse> graficas(Authentication auth) {
        return ResponseEntity.ok(gestorService.obtenerGraficas(resolverIdGestor(auth)));
    }

    @GetMapping("/solicitudes")
    public ResponseEntity<Page<SolicitudGestorResponse>> listar(
            // Una tarjeta de estado, o varias separadas por coma (PB-05: «Seguir la programación» = PROGRAMANDO,REPROGRAMADO)
            @RequestParam(required = false) String estado,
            // Texto del buscador: busca en TODAS las solicitudes del gestor, no solo en la página
            @RequestParam(required = false) String q,
            // uno o varios, separados por coma (ej. ENTREGADA_A_TIEMPO,ENTREGADA_TARDE)
            @RequestParam(required = false) java.util.List<com.polygraph.erp.shared.enums.EstadoTiempo> estadoTiempo,
            // PB-05: Vencidas · Vence hoy · Vence mañana · A tiempo · En pausa
            @RequestParam(required = false) com.polygraph.erp.shared.estados.BucketTiempo tiempo,
            @RequestParam(required = false) Integer idCliente,
            @RequestParam(required = false) Integer idProceso,
            @RequestParam(required = false) Long idResponsable,
            @RequestParam(required = false) String orden,
            @PageableDefault(size = 20, sort = "fechaSolicitud") Pageable pageable,
            Authentication auth) {
        return ResponseEntity.ok(gestorService.listarSolicitudes(resolverIdGestor(auth),
                new GestorService.FiltrosSolicitudes(estado, q, estadoTiempo, tiempo, idCliente, idProceso, idResponsable, orden), pageable));
    }

    /** PB-05: cuántas solicitudes hay en cada grupo de tiempo (los chips de Solicitudes), con los mismos filtros de cliente, estudio y responsable. */
    @GetMapping("/solicitudes/tiempos")
    public ResponseEntity<BandejaGestorService.Tiempos> tiempos(
            @RequestParam(required = false) Integer idCliente,
            @RequestParam(required = false) Integer idProceso,
            @RequestParam(required = false) Long idResponsable,
            Authentication auth) {
        return ResponseEntity.ok(bandejaService.tiempos(resolverIdGestor(auth), idCliente, idProceso, idResponsable));
    }

    /** PB-05: las opciones de los selectores Cliente, Estudio y Responsable. */
    @GetMapping("/solicitudes/filtros")
    public ResponseEntity<BandejaGestorService.OpcionesFiltro> opcionesFiltro(Authentication auth) {
        return ResponseEntity.ok(bandejaService.opcionesFiltro(resolverIdGestor(auth)));
    }

    /** PB-05: «Mi bandeja del día». */
    @GetMapping("/bandeja")
    public ResponseEntity<BandejaGestorService.Bandeja> bandeja(Authentication auth) {
        return ResponseEntity.ok(bandejaService.bandeja(resolverIdGestor(auth)));
    }

    /** PB-05: «Riesgo detectado» por periodo (HOY, SEMANA, MES, ANIO, TOTAL). */
    @GetMapping("/bandeja/riesgo")
    public ResponseEntity<com.polygraph.erp.modules.servicios.service.EstadisticasEstudiosService.RiesgoGestor> riesgo(
            @RequestParam(defaultValue = "MES") String periodo, Authentication auth) {
        return ResponseEntity.ok(bandejaService.riesgo(resolverIdGestor(auth), com.polygraph.erp.shared.estados.PeriodoEstadistica.de(periodo)));
    }

    /** PB-05: «Avisar al programador» de las citas sin agendar (todas, o solo las de un servicio). Queda registrado y le llega una notificación. */
    @PostMapping("/bandeja/avisar-programador")
    public ResponseEntity<BandejaGestorService.AvisoProgramador> avisarProgramador(@RequestParam(required = false) Integer idServicio, Authentication auth) {
        return ResponseEntity.ok(bandejaService.avisarProgramador(resolverIdGestor(auth), idServicio));
    }

    @GetMapping("/solicitudes/conteo")
    public ResponseEntity<ConteoEstadosResponse> conteoPorEstado(Authentication auth) {
        return ResponseEntity.ok(gestorService.contarPorEstado(resolverIdGestor(auth)));
    }

    private Long resolverIdGestor(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND))
                .getIdUsuario();
    }

    @GetMapping("/solicitudes/{id}")
    public ResponseEntity<SolicitudDetalleResponse> detalle(@PathVariable Integer id, Authentication auth) {
        return ResponseEntity.ok(solicitudService.obtenerDetalle(id, (UserDetails) auth.getPrincipal()));
    }

    @PatchMapping("/solicitudes/{id}/estado")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Integer id,
            @Valid @RequestBody CambioEstadoRequest request,
            Authentication auth) {
        cancelacionSolicitudService.cambiarEstado(id, request, (UserDetails) auth.getPrincipal());
        return ResponseEntity.noContent().build();
    }

    /** El candidato desistió: cancela lo que no ha iniciado y deja seguir lo iniciado (SE-02). */
    @PostMapping("/solicitudes/{id}/desistimiento")
    public ResponseEntity<Void> desistimiento(
            @PathVariable Integer id,
            @Valid @RequestBody com.polygraph.erp.modules.solicitudes.dto.DesistimientoRequest request,
            Authentication auth) {
        cancelacionSolicitudService.registrarDesistimiento(id, request, (UserDetails) auth.getPrincipal());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/solicitudes/{id}/ciudad")
    public ResponseEntity<SolicitudDetalleResponse> corregirCiudad(
            @PathVariable Integer id,
            @Valid @RequestBody CorregirCiudadRequest request,
            Authentication auth) {
        return ResponseEntity.ok(solicitudService.corregirCiudadCandidato(id, request.idCiudad(), (UserDetails) auth.getPrincipal()));
    }

    @PostMapping("/solicitudes/{id}/solicitar-reversion")
    public ResponseEntity<Void> solicitarReversion(
            @PathVariable Integer id,
            @Valid @RequestBody CambioEstadoRequest request,
            Authentication auth) {
        solicitudService.solicitarReversion(id, request, (UserDetails) auth.getPrincipal());
        return ResponseEntity.accepted().build();
    }

    /** Solo lectura: el gestor puede consultar el estado de las reversiones, no aprobarlas ni rechazarlas. */
    @GetMapping("/reversiones")
    public ResponseEntity<Page<ReversionSolicitudResponse>> listarReversiones(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(reversionSolicitudService.listar(estado, q, pageable));
    }

    /** Cuando un cliente ya llegó al tope de autoservicio (ClienteAdminService.
     *  MAX_ANALISTAS_CLIENTE) y le escribe al gestor pidiendo un usuario adicional — el gestor se
     *  lo crea sin tope, para su propio cliente asignado. */
    @GetMapping("/clientes/{idCliente}/usuarios")
    public ResponseEntity<List<ClienteAdminResponse.UsuarioAsociado>> usuariosDeCliente(
            @PathVariable Integer idCliente, Authentication auth) {
        return ResponseEntity.ok(clienteAdminService.listarUsuariosComoGestor(idCliente, resolverIdGestor(auth)));
    }

    @PostMapping("/clientes/{idCliente}/usuarios")
    public ResponseEntity<ClienteAdminResponse.UsuarioAsociado> crearUsuarioDeCliente(
            @PathVariable Integer idCliente,
            @Valid @RequestBody CrearUsuarioPropioRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clienteAdminService.crearUsuarioComoGestor(idCliente, resolverIdGestor(auth), request));
    }
}
