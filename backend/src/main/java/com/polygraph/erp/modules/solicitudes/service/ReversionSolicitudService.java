package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.servicios.entity.HistorialEstadoServicio;
import com.polygraph.erp.modules.servicios.entity.ReversionSolicitud;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.HistorialEstadoServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ReversionSolicitudRepository;
import com.polygraph.erp.modules.servicios.repository.ReversionSolicitudSpecifications;
import com.polygraph.erp.modules.solicitudes.dto.ReversionSolicitudResponse;
import com.polygraph.erp.modules.solicitudes.dto.RevisionReversionRequest;
import com.polygraph.erp.shared.entity.Notificacion;
import com.polygraph.erp.shared.enums.EstadoAprobacion;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.TipoPersona;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReversionSolicitudService {

    private final ReversionSolicitudRepository reversionSolicitudRepository;
    private final HistorialEstadoServicioRepository historialRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionRepository notificacionRepository;

    @Transactional(readOnly = true)
    public Page<ReversionSolicitudResponse> listar(String estado, String q, Pageable pageable) {
        EstadoAprobacion estadoEnum = (estado != null && !estado.isBlank())
                ? EstadoAprobacion.valueOf(estado.toUpperCase())
                : null;
        String qNormalizado = (q != null && !q.isBlank()) ? q.trim() : null;
        var especificacion = ReversionSolicitudSpecifications.conFiltros(estadoEnum, qNormalizado);
        return reversionSolicitudRepository.findAll(especificacion, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long contarPendientes() {
        return reversionSolicitudRepository.countByEstado(EstadoAprobacion.PENDIENTE);
    }

    public void aprobar(Long id, RevisionReversionRequest request, UserDetails userDetails) {
        ReversionSolicitud reversion = obtenerPendiente(id);
        Usuario admin = resolverUsuario(userDetails);
        Servicio servicio = reversion.getServicio();
        EstadoServicio estadoAnterior = servicio.getEstado();
        LocalDateTime ahora = LocalDateTime.now();

        servicio.setEstado(reversion.getEstadoDeseado());

        historialRepository.save(HistorialEstadoServicio.builder()
                .servicio(servicio)
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(reversion.getEstadoDeseado())
                .usuario(admin)
                .fechaCambio(ahora)
                .observacion(construirObservacion("Reversión aprobada", request.comentario()))
                .build());

        reversion.setEstado(EstadoAprobacion.APROBADA);
        reversion.setRevisadoPor(admin);
        reversion.setFechaRevision(ahora);
        reversion.setComentarioRevision(request.comentario());

        notificarSolicitante(reversion, admin, ahora, "Reversión aprobada",
                "Tu solicitud de reversión de la solicitud #" + servicio.getIdServicio() +
                " fue aprobada. Nuevo estado: " + reversion.getEstadoDeseado() + ".");

        log.info("Reversión {} aprobada por {}: servicio={}, {} → {}",
                id, userDetails.getUsername(), servicio.getIdServicio(), estadoAnterior, reversion.getEstadoDeseado());
    }

    public void rechazar(Long id, RevisionReversionRequest request, UserDetails userDetails) {
        ReversionSolicitud reversion = obtenerPendiente(id);
        Usuario admin = resolverUsuario(userDetails);
        Servicio servicio = reversion.getServicio();
        LocalDateTime ahora = LocalDateTime.now();

        reversion.setEstado(EstadoAprobacion.RECHAZADA);
        reversion.setRevisadoPor(admin);
        reversion.setFechaRevision(ahora);
        reversion.setComentarioRevision(request.comentario());

        notificarSolicitante(reversion, admin, ahora, "Reversión rechazada",
                "Tu solicitud de reversión de la solicitud #" + servicio.getIdServicio() + " fue rechazada." +
                (request.comentario() != null && !request.comentario().isBlank() ? " Motivo: " + request.comentario() : ""));

        log.info("Reversión {} rechazada por {}: servicio={}", id, userDetails.getUsername(), servicio.getIdServicio());
    }

    private ReversionSolicitud obtenerPendiente(Long id) {
        ReversionSolicitud reversion = reversionSolicitudRepository.findById(id)
                .orElseThrow(() -> new ApiException("Solicitud de reversión no encontrada", HttpStatus.NOT_FOUND));
        if (reversion.getEstado() != EstadoAprobacion.PENDIENTE) {
            throw new ApiException("Esta solicitud ya fue revisada", HttpStatus.CONFLICT);
        }
        return reversion;
    }

    private Usuario resolverUsuario(UserDetails userDetails) {
        return usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }

    private String construirObservacion(String prefijo, String comentario) {
        return comentario != null && !comentario.isBlank() ? prefijo + ": " + comentario : prefijo;
    }

    private void notificarSolicitante(ReversionSolicitud reversion, Usuario admin, LocalDateTime ahora,
                                       String titulo, String mensaje) {
        notificacionRepository.save(Notificacion.builder()
                .usuario(reversion.getSolicitadoPor())
                .tipo("SOLICITUD_REVERSION")
                .titulo(titulo)
                .mensaje(mensaje)
                .leida(false)
                .fechaCreacion(ahora)
                .realizadoPor(admin)
                .referenciaTipo("SERVICIO")
                .referenciaId(reversion.getServicio().getIdServicio().longValue())
                .build());
    }

    private ReversionSolicitudResponse toResponse(ReversionSolicitud r) {
        Servicio s = r.getServicio();
        return new ReversionSolicitudResponse(
                r.getId(),
                s.getIdServicio(),
                s.getCandidato() != null ? s.getCandidato().getCedula()     : null,
                s.getCandidato() != null ? s.getCandidato().getNombres()    : null,
                s.getCandidato() != null ? s.getCandidato().getApellidos() : null,
                s.getCargo(),
                nombreCliente(s.getCliente()),
                s.getProceso() != null ? s.getProceso().getNombreProceso() : null,
                s.getEstado() != null ? s.getEstado().name() : null,
                r.getEstadoDeseado().name(),
                r.getMotivo(),
                r.getEstado().name(),
                nombreUsuario(r.getSolicitadoPor()),
                r.getFechaSolicitud(),
                nombreUsuario(r.getRevisadoPor()),
                r.getFechaRevision(),
                r.getComentarioRevision());
    }

    private String nombreUsuario(Usuario u) {
        if (u == null) return null;
        return u.getNombre() + (u.getApellido() != null ? " " + u.getApellido() : "");
    }

    private String nombreCliente(Cliente c) {
        if (c == null) return null;
        if (TipoPersona.JURIDICA.equals(c.getTipoPersona()) && c.getRazonSocial() != null) {
            return c.getRazonSocial();
        }
        String nombre = c.getNombre() != null ? c.getNombre() : "";
        String apellido = c.getApellido() != null ? " " + c.getApellido() : "";
        return (nombre + apellido).trim();
    }
}
