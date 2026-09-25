package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.TipoNovedad;
import com.polygraph.erp.modules.catalogo.repository.TipoNovedadRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.gestor.dto.AgregarSeguimientoRequest;
import com.polygraph.erp.modules.gestor.dto.NovedadSubprocesoResponse;
import com.polygraph.erp.modules.gestor.dto.NovedadesSubprocesoResponse;
import com.polygraph.erp.modules.gestor.dto.RegistrarNovedadRequest;
import com.polygraph.erp.modules.gestor.dto.ResolverNovedadRequest;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.entity.NovedadSeguimiento;
import com.polygraph.erp.modules.servicios.entity.NovedadSubproceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.NovedadSeguimientoRepository;
import com.polygraph.erp.modules.servicios.repository.NovedadSubprocesoRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.shared.enums.EstadoAsignacion;
import com.polygraph.erp.shared.enums.EstadoNovedad;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoSeguimientoNovedad;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Novedades de un subproceso: quien lo ejecuta (analista/poligrafista/visitador) reporta lo que
 * pasó (ej. "el candidato nunca llegó") y quien lo coordina (gestor del cliente / programador
 * responsable) la atiende y la marca resuelta. A propósito NO cambia el estado del subproceso ni
 * bloquea completarlo — reprogramar o cancelar sigue siendo una decisión de quien coordina, desde
 * su propia agenda; esto solo deja el registro ordenado y avisa a las personas correctas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NovedadSubprocesoService {

    private static final Set<Rol> ROLES_EJECUTORES = Set.of(Rol.ANALISTA_INTERNO, Rol.POLIGRAFISTA, Rol.VISITADOR);
    private static final Set<Rol> ROLES_PRESENCIALES = Set.of(Rol.POLIGRAFISTA, Rol.VISITADOR);
    /** Una novedad se puede reportar mientras el subproceso está en curso o recién completado
     *  (ej. se acuerda de algo después de cerrarlo). Una vez PUBLICADO o CANCELADO ya no. */
    private static final Set<EstadoAsignacion> ESTADOS_ADMITEN_NOVEDAD = Set.of(EstadoAsignacion.ASIGNADO, EstadoAsignacion.COMPLETADO);
    private static final int LARGO_MAXIMO_EN_NOTIFICACION = 180;

    private final NovedadSubprocesoRepository novedadRepository;
    private final ServicioSubprocesoRepository subprocesoRepository;
    private final NotificacionService notificacionService;
    private final NovedadSeguimientoRepository seguimientoRepository;
    private final AsignacionService asignacionService;
    private final TipoNovedadRepository tipoNovedadRepository;

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Transactional(readOnly = true)
    public NovedadesSubprocesoResponse listar(Long idServicioSubproceso, Usuario actor) {
        ServicioSubproceso subproceso = obtenerOFallar(idServicioSubproceso);
        validarAccesoLectura(subproceso, actor);

        Rol rolResponsable = subproceso.getTipoProgreso().getRolResponsable();
        // Solo tipos activos, de categorías activas, que apliquen al rol de ESTE subproceso — en el
        // orden del catálogo (categoría, luego tipo).
        List<NovedadesSubprocesoResponse.TipoNovedadResponse> tipos = tipoNovedadRepository
                .findAllByOrderByCategoria_OrdenAscOrdenAscNombreAsc().stream()
                .filter(t -> Boolean.TRUE.equals(t.getActivo()) && Boolean.TRUE.equals(t.getCategoria().getActivo()))
                .filter(t -> t.aplicaA(rolResponsable))
                .map(t -> new NovedadesSubprocesoResponse.TipoNovedadResponse(
                        t.getIdTipoNovedad(), t.getNombre(), t.getCategoria().getClave(), t.getCategoria().getNombre(),
                        t.getCategoria().getColor(), t.getTextoAyuda()))
                .toList();

        // Analista/poligrafista/visitador solo ven las novedades que ellos mismos registraron —
        // si el subproceso se reasignó, las del ejecutor anterior (y las respuestas que recibió)
        // no son asunto suyo. Gestor/programador/admin ven todas. Más reciente primero.
        boolean soloPropias = ROLES_EJECUTORES.contains(actor.getRol());
        List<NovedadSubproceso> visibles = novedadRepository
                .findByServicioSubproceso_IdOrderByFechaRegistroAsc(idServicioSubproceso).stream()
                .filter(n -> !soloPropias || (n.getRegistradoPor() != null
                        && n.getRegistradoPor().getIdUsuario().equals(actor.getIdUsuario())))
                .sorted(Comparator.comparing(NovedadSubproceso::getFechaRegistro).reversed())
                .toList();
        Map<Long, List<NovedadSeguimiento>> seguimientosPorNovedad = visibles.isEmpty() ? Map.of()
                : seguimientoRepository.findByNovedad_IdInOrderByFechaAscIdAsc(visibles.stream().map(NovedadSubproceso::getId).toList())
                        .stream().collect(Collectors.groupingBy(sg -> sg.getNovedad().getId()));
        List<NovedadSubprocesoResponse> novedades = visibles.stream()
                .map(n -> toResponse(n, seguimientosPorNovedad.getOrDefault(n.getId(), List.of())))
                .toList();

        boolean puedeResolver = puedeResolver(subproceso, actor);
        return new NovedadesSubprocesoResponse(novedades, tipos, puedeRegistrar(subproceso, actor), puedeResolver,
                puedeResolver || esEjecutorAsignado(subproceso, actor),
                puedeResolver && subproceso.getEstado() == EstadoAsignacion.ASIGNADO,
                subproceso.getFechaProgramada());
    }

    @Transactional
    public NovedadSubprocesoResponse registrar(Long idServicioSubproceso, RegistrarNovedadRequest req, Usuario actor) {
        ServicioSubproceso subproceso = obtenerOFallar(idServicioSubproceso);
        if (!esEjecutorAsignado(subproceso, actor)) {
            throw new ApiException("Solo quien tiene asignado este subproceso puede reportar novedades.", HttpStatus.FORBIDDEN);
        }
        if (!ESTADOS_ADMITEN_NOVEDAD.contains(subproceso.getEstado())) {
            throw new ApiException("Este subproceso ya está " + subproceso.getEstado().name().toLowerCase()
                    + " — no admite novedades nuevas.", HttpStatus.CONFLICT);
        }

        TipoNovedad tipo = tipoNovedadRepository.findById(req.idTipoNovedad())
                .orElseThrow(() -> new ApiException("Tipo de novedad no válido.", HttpStatus.BAD_REQUEST));
        if (!Boolean.TRUE.equals(tipo.getActivo()) || !Boolean.TRUE.equals(tipo.getCategoria().getActivo())) {
            throw new ApiException("\"" + tipo.getNombre() + "\" ya no está disponible — elige otro tipo.", HttpStatus.BAD_REQUEST);
        }
        if (!tipo.aplicaA(subproceso.getTipoProgreso().getRolResponsable())) {
            throw new ApiException("\"" + tipo.getNombre() + "\" no aplica a este tipo de subproceso.", HttpStatus.BAD_REQUEST);
        }

        NovedadSubproceso novedad = novedadRepository.save(NovedadSubproceso.builder()
                .servicioSubproceso(subproceso)
                .tipo(tipo)
                .descripcion(req.descripcion().trim())
                .registradoPor(actor)
                .build());

        notificarCoordinadores(subproceso, novedad, actor);
        log.info("Novedad registrada: id={}, subproceso={}, tipo={}, actor={}",
                novedad.getId(), idServicioSubproceso, tipo.getClave(), actor.getIdUsuario());
        return toResponse(novedad, List.of());
    }

    @Transactional
    public NovedadSubprocesoResponse resolver(Long idServicioSubproceso, Long idNovedad, ResolverNovedadRequest req, Usuario actor) {
        ServicioSubproceso subproceso = obtenerOFallar(idServicioSubproceso);
        if (!puedeResolver(subproceso, actor)) {
            throw new ApiException("No tienes permiso para resolver novedades de este subproceso.", HttpStatus.FORBIDDEN);
        }
        NovedadSubproceso novedad = obtenerNovedad(idServicioSubproceso, idNovedad);
        if (novedad.getEstado() == EstadoNovedad.RESUELTA) {
            throw new ApiException("Esta novedad ya estaba resuelta.", HttpStatus.CONFLICT);
        }

        // Reprogramar es opcional: si viene una nueva fecha, se corre la hora del subproceso
        // (misma validación de agenda que asignar) y queda en el seguimiento de la novedad.
        String textoReprogramacion = "";
        boolean noRealizado = Boolean.TRUE.equals(req.marcarNoRealizado());
        if (noRealizado && req.nuevaFechaProgramada() != null) {
            throw new ApiException("Elige una sola cosa: reprogramar el subproceso o marcarlo como No realizado.", HttpStatus.BAD_REQUEST);
        }
        if (noRealizado) {
            asignacionService.marcarNoRealizado(subproceso, req.respuesta().trim(), actor);
            textoReprogramacion = " El subproceso quedó como no realizado.";
        }
        if (req.nuevaFechaProgramada() != null) {
            LocalDateTime anterior = asignacionService.reprogramar(subproceso, req.nuevaFechaProgramada(), actor,
                    "Novedad: " + novedad.getTipo().getNombre());
            seguimientoRepository.save(NovedadSeguimiento.builder()
                    .novedad(novedad)
                    .tipo(TipoSeguimientoNovedad.REPROGRAMACION)
                    .texto(req.respuesta().trim())
                    .fechaAnterior(anterior)
                    .fechaNueva(req.nuevaFechaProgramada())
                    .autor(actor)
                    .build());
            textoReprogramacion = " Nueva hora: " + req.nuevaFechaProgramada().format(FORMATO_FECHA_HORA) + ".";
        }

        novedad.setEstado(EstadoNovedad.RESUELTA);
        novedad.setRespuesta(req.respuesta().trim());
        novedad.setResueltaPor(actor);
        novedad.setFechaResolucion(LocalDateTime.now());

        Usuario reporto = novedad.getRegistradoPor();
        if (reporto != null && !reporto.getIdUsuario().equals(actor.getIdUsuario())) {
            notificacionService.crearParaUsuario(reporto.getIdUsuario(), "NOVEDAD_RESUELTA",
                    "Novedad resuelta: " + novedad.getTipo().getNombre(),
                    nombreCompleto(actor) + " respondió tu novedad en " + subproceso.getTipoProgreso().getNombreProgreso()
                            + ": " + recortar(novedad.getRespuesta()) + textoReprogramacion,
                    "SERVICIO_SUBPROCESO", subproceso.getId());
        }

        log.info("Novedad resuelta: id={}, subproceso={}, actor={}, reprogramada={}",
                idNovedad, idServicioSubproceso, actor.getIdUsuario(), req.nuevaFechaProgramada() != null);
        return toResponse(novedad, seguimientosDe(novedad));
    }

    /** Una observación más sobre una novedad ABIERTA — de quien la reportó (el ejecutor
     *  asignado) o de quien coordina. Se le avisa a "la otra parte": si escribe el ejecutor, a los
     *  coordinadores; si escribe un coordinador, a quien la reportó. */
    @Transactional
    public NovedadSubprocesoResponse agregarSeguimiento(Long idServicioSubproceso, Long idNovedad,
                                                        AgregarSeguimientoRequest req, Usuario actor) {
        ServicioSubproceso subproceso = obtenerOFallar(idServicioSubproceso);
        NovedadSubproceso novedad = obtenerNovedad(idServicioSubproceso, idNovedad);

        boolean esCoordinador = puedeResolver(subproceso, actor);
        boolean esQuienReporto = esEjecutorAsignado(subproceso, actor) && novedad.getRegistradoPor() != null
                && novedad.getRegistradoPor().getIdUsuario().equals(actor.getIdUsuario());
        if (!esCoordinador && !esQuienReporto) {
            throw new ApiException("No puedes agregar observaciones a esta novedad.", HttpStatus.FORBIDDEN);
        }
        if (novedad.getEstado() == EstadoNovedad.RESUELTA) {
            throw new ApiException("Esta novedad ya está resuelta — si pasó algo nuevo, reporta otra novedad.", HttpStatus.CONFLICT);
        }

        seguimientoRepository.save(NovedadSeguimiento.builder()
                .novedad(novedad)
                .tipo(TipoSeguimientoNovedad.OBSERVACION)
                .texto(req.texto().trim())
                .autor(actor)
                .build());

        String titulo = "Novedad actualizada: " + novedad.getTipo().getNombre();
        String mensaje = nombreCompleto(actor) + " agregó una observación en " + subproceso.getTipoProgreso().getNombreProgreso()
                + ": " + recortar(req.texto().trim());
        if (esQuienReporto) {
            destinatariosCoordinacion(subproceso, actor).forEach(id -> notificacionService.crearParaUsuario(id,
                    "NOVEDAD_REGISTRADA", titulo, mensaje, "SERVICIO_SUBPROCESO", subproceso.getId()));
        } else if (novedad.getRegistradoPor() != null && !novedad.getRegistradoPor().getIdUsuario().equals(actor.getIdUsuario())) {
            notificacionService.crearParaUsuario(novedad.getRegistradoPor().getIdUsuario(), "NOVEDAD_RESUELTA",
                    titulo, mensaje, "SERVICIO_SUBPROCESO", subproceso.getId());
        }

        log.info("Seguimiento agregado a novedad {} (subproceso {}) por {}", idNovedad, idServicioSubproceso, actor.getIdUsuario());
        return toResponse(novedad, seguimientosDe(novedad));
    }

    private NovedadSubproceso obtenerNovedad(Long idServicioSubproceso, Long idNovedad) {
        return novedadRepository.findById(idNovedad)
                .filter(n -> n.getServicioSubproceso().getId().equals(idServicioSubproceso))
                .orElseThrow(() -> new ApiException("Novedad no encontrada.", HttpStatus.NOT_FOUND));
    }

    private List<NovedadSeguimiento> seguimientosDe(NovedadSubproceso novedad) {
        return seguimientoRepository.findByNovedad_IdInOrderByFechaAscIdAsc(List.of(novedad.getId()));
    }

    /** Avisa a quien coordina el subproceso: el gestor del cliente siempre (maneja la relación
     *  con el cliente), y además el programador responsable si es poligrafía/visita (es quien
     *  puede reprogramar). Nunca a quien reportó. */
    private void notificarCoordinadores(ServicioSubproceso subproceso, NovedadSubproceso novedad, Usuario actor) {
        Set<Long> destinatarios = destinatariosCoordinacion(subproceso, actor);
        Servicio servicio = subproceso.getServicio();

        if (destinatarios.isEmpty()) {
            log.warn("Novedad {} del subproceso {} sin gestor ni programador a quién avisar", novedad.getId(), subproceso.getId());
            return;
        }

        String evaluado = nombreEvaluado(servicio);
        String mensaje = nombreCompleto(actor) + " reportó en " + subproceso.getTipoProgreso().getNombreProgreso()
                + (evaluado != null ? " (" + evaluado + ")" : "") + ": " + recortar(novedad.getDescripcion());
        destinatarios.forEach(id -> notificacionService.crearParaUsuario(id, "NOVEDAD_REGISTRADA",
                "Novedad: " + novedad.getTipo().getNombre(), mensaje, "SERVICIO_SUBPROCESO", subproceso.getId()));
    }

    /** Gestor del cliente + programador responsable, sin incluir a quien actúa. */
    private Set<Long> destinatariosCoordinacion(ServicioSubproceso subproceso, Usuario actor) {
        Set<Long> destinatarios = new HashSet<>();
        Servicio servicio = subproceso.getServicio();
        Cliente cliente = servicio != null ? servicio.getCliente() : null;
        if (cliente != null && cliente.getIdGestor() != null) destinatarios.add(cliente.getIdGestor());
        Usuario programador = subproceso.getProgramadorResponsable();
        if (programador != null) destinatarios.add(programador.getIdUsuario());
        destinatarios.remove(actor.getIdUsuario());
        return destinatarios;
    }

    /** Mismo criterio que FormularioService.validarAcceso: los ejecutores solo ven lo suyo; el
     *  resto de roles internos (el controlador ya los acota) entra en modo supervisión. */
    private void validarAccesoLectura(ServicioSubproceso subproceso, Usuario actor) {
        com.polygraph.erp.shared.utils.AccesoGestor.validar(subproceso, actor);
        if (ROLES_EJECUTORES.contains(actor.getRol()) && !esEjecutorAsignado(subproceso, actor)) {
            throw new ApiException("Este subproceso no está asignado a ti.", HttpStatus.FORBIDDEN);
        }
    }

    private boolean puedeRegistrar(ServicioSubproceso subproceso, Usuario actor) {
        return esEjecutorAsignado(subproceso, actor) && ESTADOS_ADMITEN_NOVEDAD.contains(subproceso.getEstado());
    }

    /** ADMIN_POLYGRAPH siempre; GESTOR solo si es el gestor del cliente; PROGRAMADOR solo en
     *  poligrafía/visita y si el subproceso no está encargado a otro programador. */
    private boolean puedeResolver(ServicioSubproceso subproceso, Usuario actor) {
        Rol rol = actor.getRol();
        if (rol == Rol.ADMIN_POLYGRAPH) return true;
        if (rol == Rol.GESTOR) {
            Servicio servicio = subproceso.getServicio();
            Cliente cliente = servicio != null ? servicio.getCliente() : null;
            return cliente != null && Objects.equals(cliente.getIdGestor(), actor.getIdUsuario());
        }
        if (rol == Rol.PROGRAMADOR) {
            Usuario programador = subproceso.getProgramadorResponsable();
            return ROLES_PRESENCIALES.contains(subproceso.getTipoProgreso().getRolResponsable())
                    && (programador == null || programador.getIdUsuario().equals(actor.getIdUsuario()));
        }
        return false;
    }

    private boolean esEjecutorAsignado(ServicioSubproceso subproceso, Usuario actor) {
        Usuario asignado = subproceso.getUsuarioAsignado();
        return ROLES_EJECUTORES.contains(actor.getRol())
                && asignado != null && asignado.getIdUsuario().equals(actor.getIdUsuario());
    }

    private ServicioSubproceso obtenerOFallar(Long id) {
        return subprocesoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Subproceso no encontrado.", HttpStatus.NOT_FOUND));
    }

    private NovedadSubprocesoResponse toResponse(NovedadSubproceso n, List<NovedadSeguimiento> seguimientos) {
        TipoNovedad tipo = n.getTipo();
        return new NovedadSubprocesoResponse(
                n.getId(),
                tipo.getClave(),
                tipo.getNombre(),
                tipo.getCategoria().getClave(),
                tipo.getCategoria().getNombre(),
                tipo.getCategoria().getColor(),
                n.getDescripcion(),
                n.getRegistradoPor() != null ? n.getRegistradoPor().getIdUsuario() : null,
                nombreCompleto(n.getRegistradoPor()),
                n.getFechaRegistro(),
                n.getEstado().name(),
                n.getRespuesta(),
                nombreCompleto(n.getResueltaPor()),
                n.getFechaResolucion(),
                seguimientos.stream().map(sg -> new NovedadSubprocesoResponse.SeguimientoResponse(
                        sg.getId(),
                        sg.getTipo().name(),
                        sg.getTexto(),
                        sg.getFechaAnterior(),
                        sg.getFechaNueva(),
                        nombreCompleto(sg.getAutor()),
                        sg.getAutor() != null && sg.getAutor().getRol() != null ? sg.getAutor().getRol().name() : null,
                        sg.getFecha())).toList());
    }

    private static String nombreCompleto(Usuario u) {
        if (u == null) return null;
        return (u.getNombre() + (u.getApellido() != null ? " " + u.getApellido() : "")).trim();
    }

    private static String nombreEvaluado(Servicio servicio) {
        Candidato candidato = servicio != null ? servicio.getCandidato() : null;
        if (candidato == null) return null;
        String nombre = (candidato.getNombres() + " " + (candidato.getApellidos() != null ? candidato.getApellidos() : "")).trim();
        return nombre.isBlank() ? null : nombre;
    }

    private static String recortar(String texto) {
        if (texto == null) return "";
        return texto.length() <= LARGO_MAXIMO_EN_NOTIFICACION ? texto : texto.substring(0, LARGO_MAXIMO_EN_NOTIFICACION) + "…";
    }
}
