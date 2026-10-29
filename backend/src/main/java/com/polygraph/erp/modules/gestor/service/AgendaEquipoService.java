package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.gestor.dto.AgendaEquipoDtos.BloqueDia;
import com.polygraph.erp.modules.gestor.dto.AgendaEquipoDtos.DiaNoHabil;
import com.polygraph.erp.modules.gestor.dto.AgendaEquipoDtos.DiaPersona;
import com.polygraph.erp.modules.gestor.dto.AgendaEquipoDtos.EquipoHoy;
import com.polygraph.erp.modules.gestor.dto.AgendaEquipoDtos.MiembroEquipo;
import com.polygraph.erp.modules.gestor.dto.AsignacionResponse;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.estados.ReglasAgenda;
import com.polygraph.erp.shared.estados.ReglasAgenda.Bloque;
import com.polygraph.erp.shared.estados.ReglasAgenda.Programado;
import com.polygraph.erp.shared.estados.ReglasAgenda.TipoBloque;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.service.CalendarioHabilService;
import com.polygraph.erp.shared.utils.AccesoGestor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * La Agenda del equipo (SE-08): quién está ocupado y libre hoy (jornada 7:00 a. m. – 6:00 p. m.), el día de una persona por bloques, lo
 * «atrasado en agenda» que hay que reprogramar, y qué días no se trabaja. El gestor ve a los analistas; el programador, a los poligrafistas y
 * visitadores; el administrador, a todos. La lógica de los bloques vive en {@link ReglasAgenda}; aquí solo se arman los datos.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgendaEquipoService {


    private final UsuarioRepository usuarioRepository;
    private final ServicioSubprocesoRepository repository;
    private final AsignacionService asignacionService;
    private final CalendarioHabilService calendario;

    // ───────────────────────── equipo hoy ─────────────────────────

    /** @param rolFiltro null = todos los roles que ve este usuario */
    public EquipoHoy equipoHoy(Usuario actor, LocalDate fecha, Rol rolFiltro) {
        LocalDate dia = fecha != null ? fecha : calendario.hoy();
        List<Usuario> personas = personasVisibles(actor, rolFiltro);
        Map<Long, List<ServicioSubproceso>> delDia = programadosDelDia(dia).stream()
                .filter(sp -> sp.getUsuarioAsignado() != null)
                .collect(Collectors.groupingBy(sp -> sp.getUsuarioAsignado().getIdUsuario()));
        Map<Long, Long> atrasados = atrasados(actor).stream().filter(sp -> sp.getUsuarioAsignado() != null)
                .collect(Collectors.groupingBy(sp -> sp.getUsuarioAsignado().getIdUsuario(), Collectors.counting()));

        List<MiembroEquipo> equipo = new ArrayList<>();
        for (Usuario u : personas) {
            List<ServicioSubproceso> citas = delDia.getOrDefault(u.getIdUsuario(), List.of());
            List<Bloque> bloques = ReglasAgenda.armarDia(dia, programados(citas, actor), ReglasAgenda.DESPLAZAMIENTO_MINUTOS);
            equipo.add(new MiembroEquipo(u.getIdUsuario(), u.getNombre(), u.getApellido(), u.getRol().name(),
                    ReglasAgenda.minutos(bloques, TipoBloque.OCUPADO), ReglasAgenda.minutos(bloques, TipoBloque.LIBRE),
                    ReglasAgenda.minutos(bloques, TipoBloque.BLOQUEADO), citas.size(), atrasados.getOrDefault(u.getIdUsuario(), 0L).intValue()));
        }
        equipo.sort(Comparator.comparing((MiembroEquipo m) -> m.nombre() == null ? "" : m.nombre().toLowerCase())
                .thenComparing(m -> m.apellido() == null ? "" : m.apellido().toLowerCase()));
        return new EquipoHoy(dia, calendario.esDiaHabil(dia), motivoNoHabil(dia), equipo);
    }

    // ───────────────────────── el día de una persona ─────────────────────────

    public DiaPersona diaDePersona(Usuario actor, Long idUsuario, LocalDate fecha) {
        LocalDate dia = fecha != null ? fecha : calendario.hoy();
        Usuario persona = personasVisibles(actor, null).stream().filter(u -> u.getIdUsuario().equals(idUsuario)).findFirst()
                .orElseThrow(() -> new ApiException("Esa persona no está en tu equipo.", HttpStatus.NOT_FOUND));
        List<ServicioSubproceso> citas = programadosDelDia(dia).stream()
                .filter(sp -> sp.getUsuarioAsignado() != null && sp.getUsuarioAsignado().getIdUsuario().equals(idUsuario)).toList();
        List<Bloque> bloques = ReglasAgenda.armarDia(dia, programados(citas, actor), ReglasAgenda.DESPLAZAMIENTO_MINUTOS);
        List<BloqueDia> dto = bloques.stream().map(b -> new BloqueDia(b.tipo().name(), b.inicio(), b.fin(), b.minutos(), b.motivo(),
                b.idSubproceso(), b.subproceso(), b.evaluado(), b.idServicio())).toList();
        return new DiaPersona(persona.getIdUsuario(), persona.getNombre(), persona.getApellido(), persona.getRol().name(), dia,
                calendario.esDiaHabil(dia), motivoNoHabil(dia), dia.atTime(ReglasAgenda.JORNADA_INICIO), dia.atTime(ReglasAgenda.JORNADA_FIN),
                ReglasAgenda.minutos(bloques, TipoBloque.OCUPADO), ReglasAgenda.minutos(bloques, TipoBloque.LIBRE),
                ReglasAgenda.minutos(bloques, TipoBloque.BLOQUEADO), dto);
    }

    // ───────────────────────── atrasado en agenda ─────────────────────────

    /** La bandeja «Para reprogramar»: lo que estaba programado para un día ya pasado y no se hizo. Gestor: análisis de sus clientes; programador: citas que coordina. */
    public List<AsignacionResponse> paraReprogramar(Usuario actor) {
        return atrasados(actor).stream()
                .sorted(Comparator.comparing(ServicioSubproceso::getFechaProgramada))      // el más atrasado primero
                .map(asignacionService::toResponse).toList();
    }

    // ───────────────────────── días que no se trabaja ─────────────────────────

    /** Sábados, domingos y festivos entre las dos fechas (incluidas): el cronograma no los muestra como días de trabajo. */
    public List<DiaNoHabil> diasNoHabiles(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)) throw new ApiException("Indica un rango de fechas válido.", HttpStatus.BAD_REQUEST);
        if (desde.plusDays(400).isBefore(hasta)) throw new ApiException("El rango es demasiado largo.", HttpStatus.BAD_REQUEST);
        List<DiaNoHabil> dias = new ArrayList<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            String motivo = motivoNoHabil(d);
            if (motivo != null) dias.add(new DiaNoHabil(d, motivo));
        }
        return dias;
    }

    // ───────────────────────── internos ─────────────────────────

    private String motivoNoHabil(LocalDate d) {
        if (calendario.esDiaHabil(d)) return null;
        if (d.getDayOfWeek() == DayOfWeek.SATURDAY) return "Sábado";
        if (d.getDayOfWeek() == DayOfWeek.SUNDAY) return "Domingo";
        return "Festivo";
    }

    private Set<Rol> rolesQueVe(Usuario actor) {
        return switch (actor.getRol()) {
            case GESTOR -> Set.of(Rol.ANALISTA_INTERNO);
            case PROGRAMADOR -> Set.of(Rol.POLIGRAFISTA, Rol.VISITADOR);
            case ADMIN_POLYGRAPH -> Set.of(Rol.ANALISTA_INTERNO, Rol.POLIGRAFISTA, Rol.VISITADOR);
            default -> throw new ApiException("Tu perfil no ve la agenda del equipo.", HttpStatus.FORBIDDEN);
        };
    }

    /** Las personas activas que este usuario puede ver (TODAS las del rol, no solo las que ya tienen trabajo esta semana). */
    private List<Usuario> personasVisibles(Usuario actor, Rol rolFiltro) {
        Set<Rol> roles = rolesQueVe(actor);
        if (rolFiltro != null) {
            if (!roles.contains(rolFiltro)) throw new ApiException("Ese rol no está en tu equipo.", HttpStatus.FORBIDDEN);
            roles = Set.of(rolFiltro);
        }
        return usuarioRepository.findByRolIn(List.copyOf(roles)).stream().filter(u -> Boolean.TRUE.equals(u.getActivo())).toList();
    }

    /** Lo que está en ejecución y programado ese día (ocupa la agenda de quien lo hace). */
    private List<ServicioSubproceso> programadosDelDia(LocalDate dia) {
        return repository.findByFechaProgramadaBetween(dia.atStartOfDay(), dia.plusDays(1).atStartOfDay()).stream()
                .filter(sp -> sp.getEstado() == EstadoSubproceso.EN_EJECUCION).toList();
    }

    /**
     * Un gestor no ve los datos del evaluado ni del servicio de OTRO gestor: ese tiempo aparece como «Ocupado». El programador y el administrador
     * ven los detalles de todo lo que coordinan.
     */
    private List<Programado> programados(List<ServicioSubproceso> citas, Usuario actor) {
        List<Programado> lista = new ArrayList<>();
        for (ServicioSubproceso sp : citas) {
            boolean verDetalle = actor.getRol() != Rol.GESTOR || AccesoGestor.esGestorDelServicio(sp.getServicio(), actor);
            var c = sp.getServicio() != null ? sp.getServicio().getCandidato() : null;
            String evaluado = verDetalle && c != null ? ((c.getNombres() != null ? c.getNombres() : "") + " " + (c.getApellidos() != null ? c.getApellidos() : "")).trim() : null;
            lista.add(new Programado(sp.getId(), sp.getFechaProgramada(), asignacionService.duracionEfectiva(sp),
                    verDetalle ? sp.getTipoProgreso().getNombreProgreso() : "Ocupado", evaluado,
                    verDetalle && sp.getServicio() != null ? sp.getServicio().getIdServicio() : null,
                    sp.getTipoProgreso().getRolResponsable() == Rol.VISITADOR));
        }
        return lista;
    }

    /** Atrasados en agenda dentro del alcance del usuario. */
    private List<ServicioSubproceso> atrasados(Usuario actor) {
        LocalDate hoy = asignacionService.ahoraEnColombia().toLocalDate();
        Set<Rol> roles = rolesQueVe(actor);
        return repository.porEstadoYRoles(EstadoSubproceso.EN_EJECUCION, List.copyOf(roles)).stream()
                .filter(sp -> ReglasAgenda.atrasadoEnAgenda(sp.getEstado(), sp.getFechaProgramada(), hoy))
                .filter(sp -> switch (actor.getRol()) {
                    case GESTOR -> AccesoGestor.esGestorDelServicio(sp.getServicio(), actor);
                    case PROGRAMADOR -> sp.getProgramadorResponsable() == null || sp.getProgramadorResponsable().getIdUsuario().equals(actor.getIdUsuario());
                    default -> true;
                })
                .toList();
    }

}
