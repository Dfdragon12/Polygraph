package com.polygraph.erp.modules.evaluados.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.evaluados.dto.CitaCandidatoResponse;
import com.polygraph.erp.modules.evaluados.dto.SugerenciaDisponibilidadRequest;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.entity.SugerenciaDisponibilidad;
import com.polygraph.erp.modules.servicios.entity.SugerenciaDisponibilidadOpcion;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.servicios.repository.SugerenciaDisponibilidadRepository;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoConfirmacionAsistencia;
import com.polygraph.erp.shared.enums.EstadoSugerencia;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** "Mis citas" del portal del candidato: sus poligrafías y visitas del servicio del link, y la
 *  sugerencia de disponibilidad que le manda al programador. El acceso lo decide el link
 *  (EvaluadoService.servicioConAcceso): solo ve las citas de ESE servicio. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortalCandidatoService {

    /** Hasta cuántos días adelante puede proponer — más allá el programador no agenda. */
    private static final int DIAS_MAXIMOS_SUGERENCIA = 60;
    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM");

    private final EvaluadoService evaluadoService;
    private final ServicioSubprocesoRepository subprocesoRepository;
    private final SugerenciaDisponibilidadRepository sugerenciaRepository;
    private final HojaVidaEvaluadoRepository hojaVidaRepository;
    private final CiudadRepository ciudadRepository;
    private final NotificacionService notificacionService;

    public List<CitaCandidatoResponse> citas(String token) {
        Servicio servicio = evaluadoService.servicioConAcceso(token);
        List<ServicioSubproceso> citas = subprocesoRepository
                .findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(servicio.getIdServicio()).stream()
                .filter(PortalCandidatoService::esCita)
                .toList();
        if (citas.isEmpty()) return List.of();

        Map<Long, SugerenciaDisponibilidad> pendientes = sugerenciaRepository
                .findByServicioSubproceso_IdInAndEstado(citas.stream().map(ServicioSubproceso::getId).toList(),
                        EstadoSugerencia.PENDIENTE).stream()
                .collect(Collectors.toMap(s -> s.getServicioSubproceso().getId(), s -> s, (a, b) ->
                        a.getFechaCreacion().isAfter(b.getFechaCreacion()) ? a : b));
        String direccionCandidato = direccionDeVisita(servicio);

        return citas.stream().map(sp -> {
            String estado = estadoCita(sp);
            boolean visita = sp.getTipoProgreso().getRolResponsable() == Rol.VISITADOR;
            String lugar = sp.getLugar() != null && !sp.getLugar().isBlank() ? sp.getLugar()
                    // Visita sin lugar escrito: es en su casa — su propia dirección registrada.
                    : visita ? direccionCandidato : null;
            Usuario profesional = sp.getUsuarioAsignado();
            return new CitaCandidatoResponse(
                    sp.getId(),
                    visita ? "VISITA" : "POLIGRAFIA",
                    sp.getTipoProgreso().getNombreProgreso(),
                    estado,
                    sp.getFechaProgramada(),
                    sp.getDuracionMinutos() != null ? sp.getDuracionMinutos() : sp.getTipoProgreso().getMinutosEstimados(),
                    profesional != null && sp.getFechaProgramada() != null ? nombreCompleto(profesional) : null,
                    lugar,
                    puedeSugerir(estado),
                    aRespuesta(pendientes.get(sp.getId())));
        }).toList();
    }

    /** Nueva sugerencia: reemplaza la pendiente anterior de esa cita (vale la más reciente) y le
     *  avisa al programador responsable — o al gestor del cliente si todavía no hay programador. */
    @Transactional
    public CitaCandidatoResponse.Sugerencia sugerir(String token, Long idServicioSubproceso, SugerenciaDisponibilidadRequest req) {
        Servicio servicio = evaluadoService.servicioConAcceso(token);
        evaluadoService.exigirAutorizacion(servicio);
        evaluadoService.exigirHojaVidaEnviada(servicio);
        ServicioSubproceso sp = subprocesoRepository.findById(idServicioSubproceso)
                .filter(x -> x.getServicio() != null && servicio.getIdServicio().equals(x.getServicio().getIdServicio()))
                .filter(PortalCandidatoService::esCita)
                .orElseThrow(() -> new ApiException("Cita no encontrada", HttpStatus.NOT_FOUND));
        if (!puedeSugerir(estadoCita(sp))) {
            throw new ApiException("Esta cita ya no admite sugerencias de horario.", HttpStatus.CONFLICT);
        }
        LocalDate hoy = LocalDate.now();
        Set<LocalDate> vistas = new HashSet<>();
        for (SugerenciaDisponibilidadRequest.Opcion o : req.opciones()) {
            if (o.fecha().isBefore(hoy)) {
                throw new ApiException("No puedes proponer una fecha que ya pasó (" + o.fecha().format(FECHA_CORTA) + ").", HttpStatus.BAD_REQUEST);
            }
            if (o.fecha().isAfter(hoy.plusDays(DIAS_MAXIMOS_SUGERENCIA))) {
                throw new ApiException("Propón fechas dentro de los próximos " + DIAS_MAXIMOS_SUGERENCIA + " días.", HttpStatus.BAD_REQUEST);
            }
            if (!vistas.add(o.fecha())) {
                throw new ApiException("Repetiste el mismo día; elige días distintos.", HttpStatus.BAD_REQUEST);
            }
        }

        sugerenciaRepository.findFirstByServicioSubproceso_IdAndEstadoOrderByFechaCreacionDesc(sp.getId(), EstadoSugerencia.PENDIENTE)
                .ifPresent(anterior -> anterior.setEstado(EstadoSugerencia.REEMPLAZADA));

        String comentario = req.comentario() != null && !req.comentario().isBlank() ? req.comentario().trim() : null;
        SugerenciaDisponibilidad nueva = SugerenciaDisponibilidad.builder()
                .servicioSubproceso(sp)
                .comentario(comentario)
                .estado(EstadoSugerencia.PENDIENTE)
                .fechaCreacion(LocalDateTime.now())
                .build();
        req.opciones().stream()
                .sorted(Comparator.comparing(SugerenciaDisponibilidadRequest.Opcion::fecha))
                .forEach(o -> nueva.getOpciones().add(SugerenciaDisponibilidadOpcion.builder()
                        .sugerencia(nueva).fecha(o.fecha()).franja(o.franja()).build()));
        sugerenciaRepository.save(nueva);

        notificar(servicio, sp, nueva);
        log.info("Sugerencia de disponibilidad: subproceso={}, opciones={}", sp.getId(), nueva.getOpciones().size());
        return aRespuesta(nueva);
    }

    private void notificar(Servicio servicio, ServicioSubproceso sp, SugerenciaDisponibilidad s) {
        Long destino = sp.getProgramadorResponsable() != null ? sp.getProgramadorResponsable().getIdUsuario()
                : servicio.getCliente() != null ? servicio.getCliente().getIdGestor() : null;
        if (destino == null) return;
        Candidato c = servicio.getCandidato();
        String nombre = c != null ? (c.getNombres() + " " + Objects.toString(c.getApellidos(), "")).trim() : "El candidato";
        String dias = s.getOpciones().stream()
                .map(o -> o.getFecha().format(FECHA_CORTA) + " " + etiquetaFranja(o.getFranja().name()))
                .collect(Collectors.joining(", "));
        // Agrupada: si el candidato cambia su sugerencia varias veces, el programador ve UNA
        // notificación actualizada, no una por cada cambio.
        notificacionService.crearOAgruparParaUsuario(destino, "SUGERENCIA_DISPONIBILIDAD",
                "El candidato propuso horario — " + sp.getTipoProgreso().getNombreProgreso(),
                nombre + " (servicio #" + servicio.getIdServicio() + ") tiene tiempo: " + dias
                        + (s.getComentario() != null ? ". Nota: " + s.getComentario() : ""),
                "SERVICIO_SUBPROCESO", sp.getId());
    }

    // ───────────────────────────── reglas ─────────────────────────────

    /** Solo lo presencial con el candidato es una "cita": poligrafía y visita. */
    public static boolean esCita(ServicioSubproceso sp) {
        return sp.getTipoProgreso() != null && sp.getTipoProgreso().llevaCita();
    }

    static String estadoCita(ServicioSubproceso sp) {
        if (sp.getEstado() == EstadoSubproceso.CANCELADO) return "CANCELADA";
        if (sp.getEstado() == EstadoSubproceso.FINALIZADO || sp.getEstado() == EstadoSubproceso.PUBLICADO) return "REALIZADA";
        if (sp.getFechaProgramada() == null) return "POR_PROGRAMAR";
        if (sp.getEstadoConfirmacion() == EstadoConfirmacionAsistencia.REPROGRAMAR) return "POR_REPROGRAMAR";
        if (sp.getEstadoConfirmacion() == EstadoConfirmacionAsistencia.CONFIRMADO) return "CONFIRMADA";
        return "PROGRAMADA";
    }

    /** Solo se propone horario mientras la cita NO tiene fecha asignada (o el equipo marcó que hay
     *  que reprogramarla). Programada/confirmada: ya está asignada, no se muestra ni se acepta. */
    static boolean puedeSugerir(String estado) {
        return "POR_PROGRAMAR".equals(estado) || "POR_REPROGRAMAR".equals(estado);
    }

    /** Dirección registrada del candidato (la de su hoja de vida si la diligenció) — para prellenar
     *  el lugar de una visita y mostrarla cuando el programador no escribió otro. */
    /** La dirección de la visita que el cliente escribió al solicitar (NS-08); si no escribió ninguna, la del candidato. */
    public String direccionDeVisita(Servicio servicio) {
        if (servicio == null) return null;
        if (servicio.getDireccionVisita() != null && !servicio.getDireccionVisita().isBlank()) return servicio.getDireccionVisita().trim();
        return direccionDe(servicio.getCandidato());
    }

    public String direccionDe(Candidato c) {
        if (c == null) return null;
        var hoja = hojaVidaRepository.findByCandidato_IdCandidato(c.getIdCandidato()).orElse(null);
        String direccion = hoja != null && hoja.getDireccion() != null && !hoja.getDireccion().isBlank()
                ? hoja.getDireccion() : c.getDireccionResidencia();
        if (direccion == null || direccion.isBlank()) return null;
        String barrio = hoja != null ? hoja.getBarrio() : null;
        String ciudad = c.getIdCiudadResidencia() != null
                ? ciudadRepository.findById(c.getIdCiudadResidencia()).map(Ciudad::getNombreCiudad).orElse(null) : null;
        return direccion.trim()
                + (barrio != null && !barrio.isBlank() ? ", " + barrio.trim() : "")
                + (ciudad != null ? ", " + ciudad : "");
    }

    private static String nombreCompleto(Usuario u) {
        return (u.getNombre() + " " + Objects.toString(u.getApellido(), "")).trim();
    }

    private static String etiquetaFranja(String franja) {
        return switch (franja) {
            case "MANANA" -> "mañana";
            case "TARDE" -> "tarde";
            default -> "cualquier hora";
        };
    }

    public static CitaCandidatoResponse.Sugerencia aRespuesta(SugerenciaDisponibilidad s) {
        if (s == null) return null;
        return new CitaCandidatoResponse.Sugerencia(s.getComentario(), s.getFechaCreacion(),
                s.getOpciones().stream()
                        .map(o -> new CitaCandidatoResponse.Opcion(o.getFecha(), o.getFranja().name()))
                        .toList());
    }
}
