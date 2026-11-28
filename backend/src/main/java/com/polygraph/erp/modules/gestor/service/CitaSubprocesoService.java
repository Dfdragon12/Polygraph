package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.service.MotivoSubprocesoService;
import com.polygraph.erp.modules.catalogo.service.MotivoSubprocesoService.MotivoResuelto;
import com.polygraph.erp.modules.gestor.dto.AsignacionResponse;
import com.polygraph.erp.modules.gestor.dto.CitaNoRealizadaRequest;
import com.polygraph.erp.modules.gestor.dto.ReasignarCitaRequest;
import com.polygraph.erp.modules.gestor.dto.ReprogramarCitaRequest;
import com.polygraph.erp.modules.gestor.dto.ResumenCitasResponse;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.servicios.service.RegistroCambiosService;
import com.polygraph.erp.modules.usuarios.repository.UsuariosIntenosRepository;
import com.polygraph.erp.shared.enums.AccionHistorialAsignacion;
import com.polygraph.erp.shared.enums.EstadoConfirmacionAsistencia;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoMotivo;
import com.polygraph.erp.shared.estados.MaquinaEstadosSubproceso.Accion;
import com.polygraph.erp.shared.estados.MaquinaEstadosSubproceso.Marca;
import com.polygraph.erp.shared.estados.ReglasCita;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.utils.AccesoGestor;
import com.polygraph.erp.shared.utils.FechasLegibles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Lo que pasa con una cita de poligrafía, visita o VerifEye después de agendada (SE-07): confirmar que se realizó, avisar que el evaluado no ha
 * llegado, registrar que no se realizó (pasa a REPROGRAMADO y pausa el reloj del servicio), y reprogramarla (mismo profesional) o reasignarla
 * (otro profesional), siempre con motivo. Cada cambio queda en el registro de cambios (SE-03) y avisa a quien corresponde.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CitaSubprocesoService {

    static final String CODIGO_NO_SE_PRESENTO = "NO_SE_PRESENTO";
    static final String CODIGO_AVISO_NO_ASISTIRA = "AVISO_NO_ASISTIRA";
    private static final List<Rol> ROLES_CITA = List.of(Rol.POLIGRAFISTA, Rol.VISITADOR);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.forLanguageTag("es-CO"));

    private final AsignacionService asignacionService;
    private final ServicioSubprocesoRepository repository;
    private final MotivoSubprocesoService motivoService;
    private final RegistroCambiosService registroCambios;
    private final HistorialAsignacionService historialAsignacionService;
    private final NotificacionService notificacionService;
    private final UsuarioRepository usuarioRepository;
    private final UsuariosIntenosRepository empleadoRepository;

    // ───────────────────────── confirmar y avisar ─────────────────────────

    /** El profesional inicia su informe: eso confirma que la cita se realizó. Marca el subproceso «iniciado» y lo deja en EN_EJECUCION. */
    public AsignacionResponse iniciarInforme(Long id, Usuario actor) {
        ServicioSubproceso sp = asignacionService.obtenerOFallar(id);
        exigirCita(sp);
        asignacionService.validarEsDelEjecutor(sp, actor);
        if (sp.getEstado() != EstadoSubproceso.EN_EJECUCION) {
            throw new ApiException("Solo se puede iniciar el informe de una cita en ejecución (está «" + sp.getEstado().nombre() + "»).", HttpStatus.CONFLICT);
        }
        if (!Boolean.TRUE.equals(sp.getIniciado())) {
            sp.marcarIniciado(ahora());
            log.info("Cita {} confirmada: {} inició el informe", id, actor.getEmail());
        }
        return asignacionService.toResponse(sp);
    }

    /** «El evaluado no ha llegado»: el profesional le avisa al programador para que contacte al evaluado. Un solo aviso por cita. */
    public AsignacionResponse evaluadoNoHaLlegado(Long id, Usuario actor) {
        ServicioSubproceso sp = asignacionService.obtenerOFallar(id);
        exigirCita(sp);
        asignacionService.validarEsDelEjecutor(sp, actor);
        LocalDateTime ahora = ahora();
        if (sp.getAvisoNoLlegadoEn() != null) throw new ApiException("Ya le avisaste al programador de esta cita.", HttpStatus.CONFLICT);
        if (!ReglasCita.puedeAvisarQueNoHaLlegado(true, sp.getEstado(), Boolean.TRUE.equals(sp.getIniciado()), false, sp.getFechaProgramada(), ahora)) {
            throw new ApiException("Solo puedes avisar que el evaluado no ha llegado el día de la cita, ya pasada la hora y antes de iniciar el informe.",
                    HttpStatus.CONFLICT);
        }
        sp.setAvisoNoLlegadoEn(ahora);
        String msj = nombreProfesional(actor) + " avisa que " + evaluado(sp) + " no ha llegado a la cita de " + nombreTipo(sp)
                + " de las " + HORA.format(sp.getFechaProgramada()) + ". Contáctalo.";
        destinatariosProgramador(sp).forEach(p -> notificacionService.crearParaUsuario(p.getIdUsuario(), "EVALUADO_NO_HA_LLEGADO",
                "El evaluado no ha llegado", msj, "SERVICIO_SUBPROCESO", sp.getId()));
        log.info("Cita {}: el evaluado no ha llegado (aviso de {})", id, actor.getEmail());
        return asignacionService.toResponse(sp);
    }

    // ───────────────────────── cita no realizada ─────────────────────────

    /**
     * Registra que la cita no se realizó. EN_EJECUCION → REPROGRAMADO: la cita sale de la agenda del profesional (queda en «Por reprogramar»
     * del programador) y el reloj del servicio se pausa. «No se presentó» solo pasados los 15 minutos; «avisó que no asistirá» lo registra
     * quien recibe el aviso (programador o gestor).
     */
    public AsignacionResponse citaNoRealizada(Long id, CitaNoRealizadaRequest req, Usuario actor) {
        ServicioSubproceso sp = asignacionService.obtenerOFallar(id);
        exigirCita(sp);
        verificarQuePuedeActuar(sp, actor);
        MotivoResuelto motivo = motivoService.resolver(req.idMotivo(), TipoMotivo.REPROGRAMACION, req.texto());
        asignacionService.evaluar(Accion.CITA_NO_REALIZADA, sp, actor, AsignacionService.contexto(sp, actor).conMotivo(true));

        LocalDateTime ahora = ahora();
        if (CODIGO_NO_SE_PRESENTO.equals(motivo.codigo()) && !ReglasCita.pasoLaTolerancia(sp.getFechaProgramada(), ahora, ReglasCita.TOLERANCIA_MINUTOS)) {
            throw new ApiException("Todavía no pasan los " + ReglasCita.TOLERANCIA_MINUTOS + " minutos de espera después de la hora de la cita.", HttpStatus.CONFLICT);
        }
        boolean avisoDelEvaluado = CODIGO_AVISO_NO_ASISTIRA.equals(motivo.codigo());
        if (avisoDelEvaluado && !(actor.getRol() == Rol.PROGRAMADOR || actor.getRol() == Rol.GESTOR || actor.getRol() == Rol.ADMIN_POLYGRAPH)) {
            throw new ApiException("El aviso de que el evaluado no asistirá lo registra el programador o el gestor.", HttpStatus.FORBIDDEN);
        }

        Usuario profesional = sp.getUsuarioAsignado();
        sp.setEstado(EstadoSubproceso.REPROGRAMADO);
        historialAsignacionService.registrar(sp, AccionHistorialAsignacion.REPROGRAMADO, profesional, profesional,
                sp.getFechaProgramada(), sp.getFechaProgramada(), actor, "Cita no realizada: " + motivo.descripcion());
        registroCambios.registrar(sp, EstadoSubproceso.EN_EJECUCION, EstadoSubproceso.REPROGRAMADO, null, motivo.nombre(), motivo.texto(), actor);
        asignacionService.recalcularEstadoServicio(sp.getServicio(), actor, "Cita no realizada: " + motivo.nombre());

        String cita = "la cita de " + nombreTipo(sp) + " de " + evaluado(sp) + " del " + FechasLegibles.larga(sp.getFechaProgramada().toLocalDate())
                + " a las " + HORA.format(sp.getFechaProgramada());
        notificarCliente(sp, "CITA_REPROGRAMANDO", "Cita por reagendar",
                "No se pudo realizar " + cita + " (" + motivo.nombre() + "). La estamos reagendando.");
        destinatariosProgramador(sp).stream().filter(p -> !Objects.equals(p.getIdUsuario(), actor.getIdUsuario()))
                .forEach(p -> notificacionService.crearParaUsuario(p.getIdUsuario(), "CITA_REPROGRAMADO", "Cita por reprogramar",
                        "No se realizó " + cita + ": " + motivo.descripcion() + ". Reprográmala o reasígnala.", "SERVICIO_SUBPROCESO", sp.getId()));
        if (profesional != null && !Objects.equals(profesional.getIdUsuario(), actor.getIdUsuario())) {
            String[] t = avisoDelEvaluado ? com.polygraph.erp.shared.estados.TextosAviso.evaluadoNoAsistira(cita, motivo.texto())
                    : new String[] {"Tu cita no se realizó", "Se registró que no se realizó " + cita + ": " + motivo.descripcion() + ". Sale de tu agenda mientras se reagenda."};
            notificacionService.crearParaUsuario(profesional.getIdUsuario(), "CITA_REPROGRAMADO", t[0], t[1],
                    "SERVICIO_SUBPROCESO", sp.getId());
        }
        log.info("Cita {} no realizada ({}), registrada por {}", id, motivo.nombre(), actor.getEmail());
        return asignacionService.toResponse(sp);
    }

    // ───────────────────────── reprogramar y reasignar ─────────────────────────

    /** Reprograma la cita con el MISMO profesional en otra fecha: REPROGRAMADO → EN_EJECUCION con la marca «reprogramada» (suma 1 al contador). */
    public AsignacionResponse reprogramar(Long id, ReprogramarCitaRequest req, Usuario actor) {
        ServicioSubproceso sp = asignacionService.obtenerOFallar(id);
        exigirCita(sp);
        MotivoResuelto motivo = motivoService.resolver(req.idMotivo(), TipoMotivo.REPROGRAMACION, req.texto());
        asignacionService.evaluar(Accion.REPROGRAMAR, sp, actor,
                AsignacionService.contexto(sp, actor).conDiaYHora(req.fechaProgramada() != null).conMotivo(true));
        asignacionService.validarNoEnElPasado(req.fechaProgramada());
        Usuario profesional = sp.getUsuarioAsignado();
        asignacionService.validarSinChoqueDeHorario(profesional, asignacionService.duracionEfectiva(sp), req.fechaProgramada(), Set.of(sp.getId()));

        LocalDateTime anterior = sp.getFechaProgramada();
        sp.setFechaProgramada(req.fechaProgramada());
        if (req.lugar() != null && !req.lugar().isBlank()) sp.setLugar(req.lugar().trim());
        reabrirCita(sp);
        sp.setReprogramaciones((sp.getReprogramaciones() == null ? 0 : sp.getReprogramaciones()) + 1);
        historialAsignacionService.registrar(sp, AccionHistorialAsignacion.REPROGRAMADO, profesional, profesional, anterior, req.fechaProgramada(),
                actor, motivo.descripcion());
        registroCambios.registrar(sp, EstadoSubproceso.REPROGRAMADO, EstadoSubproceso.EN_EJECUCION, Marca.REPROGRAMADA, motivo.nombre(), motivo.texto(), actor);
        asignacionService.recalcularEstadoServicio(sp.getServicio(), actor, "Cita reprogramada");

        String cuando = FechasLegibles.larga(req.fechaProgramada().toLocalDate()) + " a las " + HORA.format(req.fechaProgramada());
        notificarCliente(sp, "CITA_REPROGRAMADA", "Cita reprogramada",
                "La cita de " + nombreTipo(sp) + " de " + evaluado(sp) + " quedó para el " + cuando + ".");
        if (profesional != null) {
            notificacionService.crearParaUsuario(profesional.getIdUsuario(), "CITA_REPROGRAMADA", "Cita reprogramada",
                    "La cita de " + nombreTipo(sp) + " de " + evaluado(sp) + " quedó para el " + cuando + ". Motivo: " + motivo.descripcion() + ".",
                    "SERVICIO_SUBPROCESO", sp.getId());
        }
        log.info("Cita {} reprogramada a {} por {}", id, req.fechaProgramada(), actor.getEmail());
        return asignacionService.toResponse(sp);
    }

    /**
     * Reasigna la cita a OTRO profesional. La asignación del primero queda en el historial como «asignada – no realizada» y la cita sigue en
     * el mismo subproceso (EN_EJECUCION) con el nuevo profesional, para que el servicio no cuente dos subprocesos del mismo tipo.
     */
    public AsignacionResponse reasignar(Long id, ReasignarCitaRequest req, Usuario actor) {
        ServicioSubproceso sp = asignacionService.obtenerOFallar(id);
        exigirCita(sp);
        MotivoResuelto motivo = motivoService.resolver(req.idMotivo(), TipoMotivo.REPROGRAMACION, req.texto());
        asignacionService.evaluar(Accion.REASIGNAR, sp, actor,
                AsignacionService.contexto(sp, actor).conResponsable(req.idUsuarioAsignado() != null).conDiaYHora(req.fechaProgramada() != null).conMotivo(true));
        Usuario anterior = sp.getUsuarioAsignado();
        if (anterior != null && anterior.getIdUsuario().equals(req.idUsuarioAsignado())) {
            throw new ApiException("Para el mismo profesional usa «Reprogramar»; reasignar es para elegir a otra persona.", HttpStatus.BAD_REQUEST);
        }
        Usuario nuevo = usuarioRepository.findById(req.idUsuarioAsignado())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        boolean tieneCapacidad = nuevo.getIdEmpleado() != null && empleadoRepository
                .findBySubprocesosAsignados_IdTipoProgresoAndActivoTrue(sp.getTipoProgreso().getIdTipoProgreso()).stream()
                .anyMatch(e -> e.getIdEmpleado().equals(nuevo.getIdEmpleado()));
        if (!tieneCapacidad) {
            throw new ApiException("El usuario seleccionado no tiene el subproceso '" + sp.getTipoProgreso().getNombreProgreso()
                    + "' asignado como capacidad.", HttpStatus.BAD_REQUEST);
        }
        asignacionService.validarNoEnElPasado(req.fechaProgramada());
        asignacionService.validarSinChoqueDeHorario(nuevo, asignacionService.duracionEfectiva(sp), req.fechaProgramada(), Set.of(sp.getId()));

        LocalDateTime fechaAnterior = sp.getFechaProgramada();
        sp.setUsuarioAsignado(nuevo);
        sp.setFechaProgramada(req.fechaProgramada());
        sp.setFechaAsignacion(ahora());
        if (req.lugar() != null && !req.lugar().isBlank()) sp.setLugar(req.lugar().trim());
        reabrirCita(sp);
        historialAsignacionService.registrar(sp, AccionHistorialAsignacion.REASIGNADO, anterior, nuevo, fechaAnterior, req.fechaProgramada(), actor,
                "Asignada – no realizada. " + motivo.descripcion());
        // En el registro, el responsable del cambio es el PRIMER profesional: su asignación es la que queda «asignada – no realizada».
        registroCambios.registrar(sp, anterior, EstadoSubproceso.REPROGRAMADO, EstadoSubproceso.EN_EJECUCION, Marca.ASIGNADA_NO_REALIZADA,
                motivo.nombre(), motivo.texto(), actor);
        asignacionService.recalcularEstadoServicio(sp.getServicio(), actor, "Cita reasignada");

        String cuando = FechasLegibles.larga(req.fechaProgramada().toLocalDate()) + " a las " + HORA.format(req.fechaProgramada());
        notificarCliente(sp, "CITA_REPROGRAMADA", "Cita reprogramada",
                "La cita de " + nombreTipo(sp) + " de " + evaluado(sp) + " quedó para el " + cuando + ".");
        notificacionService.crearParaUsuario(nuevo.getIdUsuario(), "SUBPROCESO_ASIGNADO", nombreTipo(sp) + " asignado",
                "Te asignaron la cita de " + nombreTipo(sp) + " de " + evaluado(sp) + " para el " + cuando + ".", "SERVICIO_SUBPROCESO", sp.getId());
        if (anterior != null) {
            notificacionService.crearParaUsuario(anterior.getIdUsuario(), "SUBPROCESO_DESASIGNADO", "Cita reasignada",
                    "La cita de " + nombreTipo(sp) + " de " + evaluado(sp) + " pasó a otro profesional: ya no tienes que hacerla.", "SERVICIO_SUBPROCESO", sp.getId());
        }
        log.info("Cita {} reasignada de {} a {} por {}", id, anterior != null ? anterior.getEmail() : null, nuevo.getEmail(), actor.getEmail());
        return asignacionService.toResponse(sp);
    }

    // ───────────────────────── bandejas ─────────────────────────

    /** La bandeja «Por confirmar» de quien la ve: el programador, las citas que coordina; el profesional, las suyas; el gestor, las de sus clientes. */
    @Transactional(readOnly = true)
    public List<AsignacionResponse> porConfirmar(Usuario actor) {
        return citasPorConfirmar(actor).stream().map(asignacionService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ResumenCitasResponse resumen(Usuario actor) {
        long porReprogramar = repository.porEstadoYRoles(EstadoSubproceso.REPROGRAMADO, ROLES_CITA).stream()
                .filter(sp -> esDeQuien(sp, actor)).count();
        return new ResumenCitasResponse(citasPorConfirmar(actor).size(), porReprogramar);
    }

    /**
     * Avisa UNA vez, al programador y al profesional, de las citas que pasaron a «Por confirmar». Lo llama la tarea programada
     * cada minuto; el aviso se marca en la cita para no repetirse.
     */
    public int avisarPorConfirmar() {
        LocalDateTime limite = ahora().minusMinutes(ReglasCita.TOLERANCIA_MINUTOS);
        int avisadas = 0;
        for (ServicioSubproceso sp : repository.citasSinConfirmar(EstadoSubproceso.EN_EJECUCION, limite, ROLES_CITA)) {
            if (sp.getAvisoPorConfirmarEn() != null) continue;
            sp.setAvisoPorConfirmarEn(ahora());
            String msj = "La cita de " + nombreTipo(sp) + " de " + evaluado(sp) + " de las " + HORA.format(sp.getFechaProgramada()) + " del "
                    + FechasLegibles.larga(sp.getFechaProgramada().toLocalDate()) + " quedó «Por confirmar»: regístrala como realizada o no realizada.";
            destinatariosProgramador(sp).forEach(p -> notificacionService.crearParaUsuario(p.getIdUsuario(), "CITA_POR_CONFIRMAR", "Cita por confirmar",
                    msj, "SERVICIO_SUBPROCESO", sp.getId()));
            if (sp.getUsuarioAsignado() != null) {
                notificacionService.crearParaUsuario(sp.getUsuarioAsignado().getIdUsuario(), "CITA_POR_CONFIRMAR", "Cita por confirmar", msj,
                        "SERVICIO_SUBPROCESO", sp.getId());
            }
            avisadas++;
        }
        if (avisadas > 0) log.info("Citas por confirmar avisadas: {}", avisadas);
        return avisadas;
    }

    // ───────────────────────── internos ─────────────────────────

    private List<ServicioSubproceso> citasPorConfirmar(Usuario actor) {
        LocalDateTime limite = ahora().minusMinutes(ReglasCita.TOLERANCIA_MINUTOS);
        return repository.citasSinConfirmar(EstadoSubproceso.EN_EJECUCION, limite, ROLES_CITA).stream().filter(sp -> esDeQuien(sp, actor)).toList();
    }

    /** ¿Esta cita le incumbe a este usuario? Programador: la que coordina (o la que nadie coordina); profesional: la suya; gestor: las de sus clientes. */
    private boolean esDeQuien(ServicioSubproceso sp, Usuario actor) {
        return switch (actor.getRol()) {
            case ADMIN_POLYGRAPH -> true;
            case PROGRAMADOR -> sp.getProgramadorResponsable() == null || sp.getProgramadorResponsable().getIdUsuario().equals(actor.getIdUsuario());
            case POLIGRAFISTA, VISITADOR -> sp.getUsuarioAsignado() != null && sp.getUsuarioAsignado().getIdUsuario().equals(actor.getIdUsuario());
            case GESTOR -> AccesoGestor.esGestorDelServicio(sp.getServicio(), actor);
            default -> false;
        };
    }

    private void verificarQuePuedeActuar(ServicioSubproceso sp, Usuario actor) {
        switch (actor.getRol()) {
            case POLIGRAFISTA, VISITADOR -> asignacionService.validarEsDelEjecutor(sp, actor);
            case GESTOR -> AccesoGestor.validar(sp, actor);
            case PROGRAMADOR -> {
                if (sp.getProgramadorResponsable() != null && !sp.getProgramadorResponsable().getIdUsuario().equals(actor.getIdUsuario())) {
                    throw new ApiException("Esta cita la coordina otro programador.", HttpStatus.FORBIDDEN);
                }
            }
            default -> { /* el administrador y los demás los valida la máquina de estados */ }
        }
    }

    /** La cita vuelve a estar en pie con su nueva fecha: la llamada de confirmación se repite y los avisos de la fecha anterior se limpian. */
    private void reabrirCita(ServicioSubproceso sp) {
        sp.setEstado(EstadoSubproceso.EN_EJECUCION);
        sp.setAvisoNoLlegadoEn(null);
        sp.setAvisoPorConfirmarEn(null);
        sp.setEstadoConfirmacion(EstadoConfirmacionAsistencia.SIN_LLAMAR);
    }

    private static void exigirCita(ServicioSubproceso sp) {
        if (!AsignacionService.esCita(sp)) {
            throw new ApiException("Solo la poligrafía, la visita y VerifEye tienen cita.", HttpStatus.BAD_REQUEST);
        }
    }

    private LocalDateTime ahora() {
        return asignacionService.ahoraEnColombia();
    }

    /** A quién le toca coordinar esta cita: el programador encargado o, si nadie lo está, todos los programadores activos. */
    private List<Usuario> destinatariosProgramador(ServicioSubproceso sp) {
        if (sp.getProgramadorResponsable() != null) return List.of(sp.getProgramadorResponsable());
        return usuarioRepository.findByRolIn(List.of(Rol.PROGRAMADOR)).stream().filter(u -> Boolean.TRUE.equals(u.getActivo())).toList();
    }

    private void notificarCliente(ServicioSubproceso sp, String tipo, String titulo, String mensaje) {
        Servicio s = sp.getServicio();
        if (s != null && s.getCliente() != null) notificacionService.crearParaCliente(s.getCliente().getIdCliente(), tipo, titulo, mensaje);
    }

    private static String nombreTipo(ServicioSubproceso sp) {
        return sp.getTipoProgreso().getNombreProgreso();
    }

    private static String evaluado(ServicioSubproceso sp) {
        var c = sp.getServicio() != null ? sp.getServicio().getCandidato() : null;
        if (c == null) return "el evaluado";
        return ((c.getNombres() != null ? c.getNombres() : "") + " " + (c.getApellidos() != null ? c.getApellidos() : "")).trim();
    }

    private static String nombreProfesional(Usuario u) {
        return ((u.getNombre() != null ? u.getNombre() : "") + (u.getApellido() != null ? " " + u.getApellido() : "")).trim();
    }
}
