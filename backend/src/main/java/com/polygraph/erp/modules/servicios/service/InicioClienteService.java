package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.gestor.service.EnvioEnlaceService;
import com.polygraph.erp.modules.gestor.service.EnvioEnlaceService.SeguimientoCliente;
import com.polygraph.erp.modules.riesgo.entity.CalificacionRiesgo;
import com.polygraph.erp.modules.riesgo.repository.CalificacionRiesgoRepository;
import com.polygraph.erp.modules.servicios.dto.ServicioSemaforoFila.ResumenEstado;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.shared.dto.TiempoServicio;
import com.polygraph.erp.shared.enums.Cumplimiento;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.PastaEstado;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.estados.BucketTiempo;
import com.polygraph.erp.shared.estados.PeriodoEstadistica;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.service.TiempoServicioService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Inicio del cliente (PB-05): «Informes listos», «Requiere tu atención» y «Tiempos de entrega», todo con el alcance de SU empresa y reutilizando lo que
 * ya calculan el semáforo, la publicación por subproceso, la pasta y el seguimiento del enlace. Nada de estados nuevos. El cliente solo MIRA el enlace:
 * enviarlo o reenviarlo lo hace Polygraph.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InicioClienteService {

    static final ZoneId ZONA = ZoneId.of("America/Bogota");
    static final int INFORMES = 3;
    static final int TARJETAS = 5;

    @PersistenceContext
    private EntityManager em;

    private final UsuarioRepository usuarioRepository;
    private final ServicioSubprocesoRepository subprocesoRepository;
    private final CalificacionRiesgoRepository calificacionRepository;
    private final TiempoServicioService tiempoServicioService;
    private final EnvioEnlaceService envioEnlaceService;
    private final EstadisticasEstudiosService estadisticasService;
    private final Clock reloj;

    // ───────────────────────── respuesta ─────────────────────────

    public record Inicio(long informesNuevos, long cosasPorAtender, List<Informe> informes, List<CandidatoPendiente> candidatos, List<Cita> citas, Tiempos tiempos) {}

    /** completo: todos los subprocesos publicados y la pasta emitida («Publicado · estudio completo»); si no, «Publicado parcial · x de y informes». */
    public record Informe(Integer idServicio, String evaluado, String proceso, String cargo, int publicados, int vigentes, boolean completo, String nivel,
                          LocalDateTime emitidoEn, String ultimoNombre, LocalDateTime ultimoFecha, Cumplimiento cumplimiento, boolean nuevo) {}

    public record CandidatoPendiente(Integer idServicio, String evaluado, String proceso, List<ResumenEstado> resumen, TiempoServicio tiempo, LocalDate fechaEntrega,
                                     SeguimientoCliente seguimiento) {}

    public record Cita(Integer idServicio, String evaluado, String proceso, String subproceso, String estado, LocalDateTime fecha, String lugar) {}

    public record Tiempos(long aTiempo, long venceHoyOManana, long atrasadas, long enPausa) {}

    // ───────────────────────── inicio ─────────────────────────

    /** desde: la última vez que el cliente abrió su inicio (lo manda su navegador); lo publicado después sale marcado como nuevo. null = sin marcas. */
    public Inicio inicio(String email, LocalDateTime desde) {
        Integer idCliente = idClienteDe(email);
        long nuevos = desde == null ? 0 : (Long) em.createQuery("SELECT COUNT(sp) FROM ServicioSubproceso sp WHERE sp.servicio.cliente.idCliente = :c "
                        + "AND sp.estado = :pub AND sp.fechaPublicado > :desde").setParameter("c", idCliente).setParameter("pub", EstadoSubproceso.PUBLICADO)
                .setParameter("desde", desde).getSingleResult();
        List<CandidatoPendiente> candidatos = candidatos(idCliente);
        List<Cita> citas = citas(idCliente);
        return new Inicio(nuevos, candidatos.size() + citas.size(), informes(idCliente, desde), candidatos, citas, tiempos(idCliente));
    }

    /** Estadísticas de mi empresa por periodo (HOY, SEMANA, MES, ANIO, TOTAL). */
    public EstadisticasEstudiosService.EstadisticasCliente estadisticas(String email, PeriodoEstadistica periodo) {
        return estadisticasService.delCliente(idClienteDe(email), periodo);
    }

    // ───────────────────────── informes listos ─────────────────────────

    @SuppressWarnings("unchecked")
    private List<Informe> informes(Integer idCliente, LocalDateTime desde) {
        List<Object[]> filas = em.createQuery("SELECT sp.servicio.idServicio, MAX(sp.fechaPublicado) FROM ServicioSubproceso sp WHERE sp.servicio.cliente.idCliente = :c "
                        + "AND sp.estado = :pub GROUP BY sp.servicio.idServicio ORDER BY MAX(sp.fechaPublicado) DESC")
                .setParameter("c", idCliente).setParameter("pub", EstadoSubproceso.PUBLICADO).setMaxResults(8).getResultList();
        if (filas.isEmpty()) return List.of();
        List<Integer> ids = filas.stream().map(f -> (Integer) f[0]).toList();
        Map<Integer, List<ServicioSubproceso>> sps = subprocesoRepository.findByServicio_IdServicioInOrderByServicio_IdServicioAscTipoProgreso_OrdenAsc(ids)
                .stream().collect(Collectors.groupingBy(sp -> sp.getServicio().getIdServicio()));
        Map<Integer, CalificacionRiesgo> riesgo = calificacionRepository.findByVigenteTrueAndServicio_IdServicioIn(ids).stream()
                .collect(Collectors.toMap(c -> c.getServicio().getIdServicio(), c -> c, (a, b) -> a));

        List<Informe> lista = new ArrayList<>();
        for (Integer id : ids) {
            List<ServicioSubproceso> lista1 = sps.getOrDefault(id, List.of());
            if (lista1.isEmpty()) continue;
            Servicio s = lista1.get(0).getServicio();
            var avance = com.polygraph.erp.shared.estados.SemaforoEstados.avance(lista1.stream().map(ServicioSubproceso::getEstado).toList());
            ServicioSubproceso ultimo = lista1.stream().filter(sp -> sp.getEstado() == EstadoSubproceso.PUBLICADO && sp.getFechaPublicado() != null)
                    .max(Comparator.comparing(ServicioSubproceso::getFechaPublicado)).orElse(null);
            boolean completo = s.getPastaEstado() == PastaEstado.EMITIDA && avance.vigentes() > 0 && avance.publicados() == avance.vigentes();
            CalificacionRiesgo c = riesgo.get(id);
            var nivelVale = c == null ? null : c.getNivelDecidido() != null ? c.getNivelDecidido() : c.getNivelFinal();
            String nivel = completo && nivelVale != null ? nivelVale.name() : null;   // el estudio parcial no muestra nivel
            LocalDateTime ultimoFecha = ultimo == null ? null : ultimo.getFechaPublicado();
            boolean nuevo = desde != null && ((ultimoFecha != null && ultimoFecha.isAfter(desde)) || (completo && s.getEmitidoEn() != null && s.getEmitidoEn().isAfter(desde)));
            lista.add(new Informe(id, evaluado(s), s.getProceso() != null ? s.getProceso().getNombreProceso() : null, s.getCargo(), avance.publicados(), avance.vigentes(),
                    completo, nivel, s.getEmitidoEn(), ultimo == null ? null : ultimo.getTipoProgreso().getNombreProgreso(), ultimoFecha, s.getCumplimiento(), nuevo));
        }
        lista.sort(Comparator.comparing((Informe i) -> momento(i)).reversed());
        return lista.size() > INFORMES ? lista.subList(0, INFORMES) : lista;
    }

    private static LocalDateTime momento(Informe i) {
        LocalDateTime a = i.ultimoFecha(), b = i.emitidoEn();
        if (a == null) return b == null ? LocalDateTime.MIN : b;
        return b != null && b.isAfter(a) ? b : a;
    }

    // ───────────────────────── requiere tu atención ─────────────────────────

    @SuppressWarnings("unchecked")
    private List<CandidatoPendiente> candidatos(Integer idCliente) {
        List<LinkCandidato> links = em.createQuery("SELECT l FROM LinkCandidato l JOIN FETCH l.servicio s LEFT JOIN FETCH s.candidato LEFT JOIN FETCH s.proceso "
                        + "WHERE s.cliente.idCliente = :c AND l.usado = false AND l.revocado = false AND s.estado NOT IN :cerrados ORDER BY l.fechaCreacion DESC")
                .setParameter("c", idCliente).setParameter("cerrados", EstadoServicio.CERRADOS).setMaxResults(TARJETAS).getResultList();
        if (links.isEmpty()) return List.of();
        Map<Integer, List<ServicioSubproceso>> sps = subprocesoRepository
                .findByServicio_IdServicioInOrderByServicio_IdServicioAscTipoProgreso_OrdenAsc(links.stream().map(l -> l.getServicio().getIdServicio()).toList())
                .stream().collect(Collectors.groupingBy(sp -> sp.getServicio().getIdServicio()));
        List<CandidatoPendiente> r = new ArrayList<>();
        for (LinkCandidato l : links) {
            Servicio s = l.getServicio();
            r.add(new CandidatoPendiente(s.getIdServicio(), evaluado(s), s.getProceso() != null ? s.getProceso().getNombreProceso() : null,
                    ResumenEstado.de(sps.getOrDefault(s.getIdServicio(), List.of()).stream().map(ServicioSubproceso::getEstado).toList()),
                    tiempoServicioService.de(s), s.fechaParaMostrar(), envioEnlaceService.seguimientoParaCliente(l)));
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<Cita> citas(Integer idCliente) {
        LocalDateTime hoy = LocalDate.now(reloj.withZone(ZONA)).atStartOfDay();
        List<ServicioSubproceso> lista = em.createQuery("SELECT sp FROM ServicioSubproceso sp JOIN FETCH sp.servicio s JOIN FETCH sp.tipoProgreso t LEFT JOIN FETCH s.candidato LEFT JOIN FETCH s.proceso "
                        + "WHERE s.cliente.idCliente = :c AND t.rolResponsable IN :roles AND sp.estado IN :estados AND sp.fechaProgramada >= :hoy ORDER BY sp.fechaProgramada")
                .setParameter("c", idCliente).setParameter("roles", List.of(Rol.POLIGRAFISTA, Rol.VISITADOR))
                .setParameter("estados", List.of(EstadoSubproceso.PROGRAMANDO, EstadoSubproceso.EN_EJECUCION)).setParameter("hoy", hoy).setMaxResults(TARJETAS).getResultList();
        return lista.stream().map(sp -> new Cita(sp.getServicio().getIdServicio(), evaluado(sp.getServicio()),
                sp.getServicio().getProceso() != null ? sp.getServicio().getProceso().getNombreProceso() : null, sp.getTipoProgreso().getNombreProgreso(),
                sp.getEstado().name(), sp.getFechaProgramada(), sp.getLugar())).toList();
    }

    // ───────────────────────── tiempos de entrega ─────────────────────────

    @SuppressWarnings("unchecked")
    private Tiempos tiempos(Integer idCliente) {
        List<Object[]> filas = em.createQuery("SELECT s.estadoTiempo, s.diasRestantesHabiles FROM Servicio s WHERE s.cliente.idCliente = :c AND s.estadoTiempo IS NOT NULL")
                .setParameter("c", idCliente).getResultList();
        long aTiempo = 0, hoyManana = 0, atrasadas = 0, pausa = 0;
        for (Object[] f : filas) {
            BucketTiempo b = BucketTiempo.de((com.polygraph.erp.shared.enums.EstadoTiempo) f[0], (Integer) f[1]);
            if (b == null) continue;
            switch (b) {
                case A_TIEMPO -> aTiempo++;
                case VENCE_HOY, VENCE_MANANA -> hoyManana++;
                case VENCIDAS -> atrasadas++;
                case EN_PAUSA -> pausa++;
            }
        }
        return new Tiempos(aTiempo, hoyManana, atrasadas, pausa);
    }

    // ───────────────────────── apoyo ─────────────────────────

    private Integer idClienteDe(String email) {
        Usuario u = usuarioRepository.findByEmail(email).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (u.getIdCliente() == null) throw new ApiException("Usuario no asociado a ningún cliente", HttpStatus.FORBIDDEN);
        return u.getIdCliente();
    }

    private static String evaluado(Servicio s) {
        var c = s.getCandidato();
        if (c == null) return "—";
        return ((c.getNombres() != null ? c.getNombres() : "") + " " + (c.getApellidos() != null ? c.getApellidos() : "")).trim();
    }
}
