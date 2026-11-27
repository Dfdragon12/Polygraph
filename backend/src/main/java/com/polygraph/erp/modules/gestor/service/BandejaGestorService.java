package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.mensajes.service.MensajeService;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.riesgo.entity.CalificacionRiesgo;
import com.polygraph.erp.modules.riesgo.repository.CalificacionRiesgoRepository;
import com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService;
import com.polygraph.erp.modules.servicios.dto.ServicioSemaforoFila.ResumenEstado;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSpecifications;
import com.polygraph.erp.modules.servicios.service.ConsultaSemaforo.Filtros;
import com.polygraph.erp.modules.servicios.service.ConsultaSemaforo.Solicitante;
import com.polygraph.erp.modules.servicios.service.EstadisticasEstudiosService;
import com.polygraph.erp.modules.servicios.service.EstadisticasEstudiosService.RiesgoGestor;
import com.polygraph.erp.modules.servicios.service.SemaforoEstadoService;
import com.polygraph.erp.shared.dto.TiempoServicio;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.PastaEstado;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.estados.BucketTiempo;
import com.polygraph.erp.shared.estados.PeriodoEstadistica;
import com.polygraph.erp.shared.estados.ProximaAccion;
import com.polygraph.erp.shared.estados.ProximaAccion.Accion;
import com.polygraph.erp.shared.estados.ProximaAccion.ResponsableRetraso;
import com.polygraph.erp.shared.estados.ProximaAccion.Sub;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.service.TiempoServicioService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * «Mi bandeja del día» del gestor (PB-05). No crea lógica de estados: junta lo que ya calculan el semáforo (PB-01), la publicación por subproceso
 * (PB-02), la pasta (PB-03) y el enlace del candidato (PB-04), con el alcance de los clientes que atiende. Los números de los pasos salen del MISMO
 * resumen que las tarjetas de Solicitudes; los de tiempo, de las mismas especificaciones que sus filtros.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BandejaGestorService {

    static final int FILAS_CONTROL_CALIDAD = 10;
    static final int MUESTRAS = 3;

    @PersistenceContext
    private EntityManager em;

    private final SemaforoEstadoService semaforoService;
    private final ServicioRepository servicioRepository;
    private final HojaVidaEvaluadoRepository hojaVidaRepository;
    private final CalificacionRiesgoRepository calificacionRepository;
    private final CalificacionRiesgoService calificacionService;
    private final NovedadesTableroService novedadesTableroService;
    private final MensajeService mensajeService;
    private final TiempoServicioService tiempoServicioService;
    private final NotificacionService notificacionService;
    private final UsuarioRepository usuarioRepository;
    private final EstadisticasEstudiosService estadisticasService;
    private final JdbcTemplate jdbc;

    // ───────────────────────── respuesta ─────────────────────────

    public record Bandeja(long estudios, long mensajesSinLeer, List<PasoTrabajo> pasos, Cancelado cancelado, ControlCalidad controlCalidad,
                          Atencion atencion, Tiempos tiempos, List<Persona> equipo) {}

    /** estado(s) con que se filtra Solicitudes (coma = varios), el número de servicios y subprocesos, y el texto de apoyo. */
    public record PasoTrabajo(int numero, String titulo, String estados, long servicios, long subprocesos, String etiqueta, String detalle, boolean resaltado) {}

    public record Cancelado(long servicios, long subprocesos) {}

    public record ControlCalidad(long finalizadosPorPublicar, long pastasPendientes, List<FilaCalidad> filas) {}

    /** tipo: SUBPROCESO (un informe por publicar) o PASTA (todos publicados, falta emitir). */
    public record FilaCalidad(String tipo, Integer idServicio, Long idSubproceso, String evaluado, String cliente, String queLlego, String detalle,
                              String responsable, TiempoServicio tiempo, String riesgoNivel, java.math.BigDecimal riesgoPuntaje, String accion, String accionEtiqueta) {}

    /** devueltos (PC-04): lo que el gestor devolvió y espera la corrección del ejecutor; «Devuelto» es una marca, no un estado. */
    public record Atencion(Bloque novedades, Bloque sinAsignar, Bloque candidatosSinHojaDeVida, Bloque citasSinAgendar, Bloque devueltos) {}

    public record Bloque(long cantidad, String detalle, List<Muestra> muestras) {}

    public record Muestra(Integer idServicio, Long idSubproceso, String texto) {}

    public record Tiempos(Map<String, Long> conteo, Map<String, Long> vencidasPorResponsable) {}

    public record Persona(Long idUsuario, String nombre, String rol, long asignados, long vencidos, long enRevision) {}

    public record AvisoProgramador(int programadores, int citas) {}

    // ───────────────────────── bandeja ─────────────────────────

    @Transactional(readOnly = true)
    public Bandeja bandeja(Long idGestor) {
        Solicitante quien = new Solicitante(Rol.GESTOR, idGestor, null);
        var resumen = semaforoService.resumen(quien, Filtros.ninguno());
        var alertas = semaforoService.alertas(quien, Filtros.ninguno());
        Usuario gestor = usuarioRepository.findById(idGestor).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        Map<Integer, List<ServicioSubproceso>> porServicio = subprocesosDelGestor(idGestor);
        Map<Long, Boolean> completado = hojaVidaCompletada(porServicio.keySet().stream().map(id -> porServicio.get(id).get(0).getServicio()).toList());

        long pastasPendientes = porServicio.values().stream()
                .filter(sps -> ProximaAccion.pendienteDelGestor(estados(sps), emitida(sps.get(0).getServicio()))
                        && estados(sps).stream().filter(e -> e != EstadoSubproceso.CANCELADO).allMatch(e -> e == EstadoSubproceso.PUBLICADO))
                .count();
        long enlacesPorEnviar = contarEnlacesPorEnviar(idGestor);

        return new Bandeja(resumen.total(), mensajesSinLeer(idGestor), pasos(resumen, alertas.sinAsignar(), enlacesPorEnviar, pastasPendientes),
                cancelado(resumen), controlCalidad(gestor, porServicio, pastasPendientes), atencion(idGestor, alertas.sinAsignar(), porServicio, completado),
                tiempos(idGestor, null, null, null, porServicio, completado), equipo(porServicio));
    }

    /** Anillos de «Riesgo detectado» del gestor por periodo (Hoy · Semana · Mes · Año). */
    @Transactional(readOnly = true)
    public RiesgoGestor riesgo(Long idGestor, PeriodoEstadistica periodo) {
        return estadisticasService.delGestor(idGestor, periodo);
    }

    // ───────────────────────── pasos ─────────────────────────

    private List<PasoTrabajo> pasos(com.polygraph.erp.modules.servicios.dto.SemaforoResponse r, Long sinAsignar, long enlacesPorEnviar, long pastasPendientes) {
        Map<String, com.polygraph.erp.modules.servicios.dto.SemaforoResponse.EstadoConteo> m = r.estados().stream()
                .collect(Collectors.toMap(com.polygraph.erp.modules.servicios.dto.SemaforoResponse.EstadoConteo::codigo, e -> e));
        var pend = m.get("PENDIENTE");
        var prog = m.get("PROGRAMANDO");
        var repro = m.get("REPROGRAMADO");
        var ejec = m.get("EN_EJECUCION");
        var fin = m.get("FINALIZADO");
        var pub = m.get("PUBLICADO");
        return List.of(
                new PasoTrabajo(1, "Asignar", "PENDIENTE", pend.servicios(), pend.subprocesos(), "Pendiente",
                        sinAsignar + " subprocesos sin ejecutor o sin programador · " + enlacesPorEnviar + " enlaces por enviar", false),
                new PasoTrabajo(2, "Seguir la programación", "PROGRAMANDO,REPROGRAMADO", prog.servicios() + repro.servicios(), prog.subprocesos() + repro.subprocesos(),
                        "Programando " + prog.servicios() + " · Reprogramado " + repro.servicios(), (prog.subprocesos() + repro.subprocesos()) + " subprocesos · los agenda el programador", false),
                new PasoTrabajo(3, "Seguir la ejecución", "EN_EJECUCION", ejec.servicios(), ejec.subprocesos(), "En ejecución", ejec.subprocesos() + " subprocesos", false),
                new PasoTrabajo(4, "Revisar", "FINALIZADO", fin.servicios(), fin.subprocesos(), "Finalizado · control de calidad", fin.subprocesos() + " subprocesos", true),
                new PasoTrabajo(5, "Publicar", "PUBLICADO", pub.servicios(), pub.subprocesos(), "Publicado", pub.subprocesos() + " subprocesos · pasta pendiente en " + pastasPendientes, false));
    }

    private Cancelado cancelado(com.polygraph.erp.modules.servicios.dto.SemaforoResponse r) {
        return r.estados().stream().filter(e -> "CANCELADO".equals(e.codigo())).findFirst()
                .map(e -> new Cancelado(e.servicios(), e.subprocesos())).orElse(new Cancelado(0, 0));
    }

    private long contarEnlacesPorEnviar(Long idGestor) {
        Long n = (Long) em.createQuery("SELECT COUNT(l) FROM LinkCandidato l WHERE l.servicio.cliente.idGestor = :g AND l.enviadoEn IS NULL AND l.usado = false "
                        + "AND l.revocado = false AND l.servicio.estado NOT IN :cerrados")
                .setParameter("g", idGestor).setParameter("cerrados", EstadoServicio.CERRADOS).getSingleResult();
        return n == null ? 0 : n;
    }

    private long mensajesSinLeer(Long idGestor) {
        try {
            return mensajeService.misConversaciones(idGestor).stream().mapToLong(c -> c.noLeidos()).sum();
        } catch (RuntimeException e) {
            log.warn("No se pudieron contar los mensajes sin leer del gestor {}: {}", idGestor, e.getMessage());
            return 0;
        }
    }

    // ───────────────────────── 1. control de calidad ─────────────────────────

    private ControlCalidad controlCalidad(Usuario gestor, Map<Integer, List<ServicioSubproceso>> porServicio, long pastasPendientes) {
        List<FilaCalidad> filas = new ArrayList<>();
        long finalizados = 0;
        for (List<ServicioSubproceso> sps : porServicio.values()) {
            Servicio s = sps.get(0).getServicio();
            for (ServicioSubproceso sp : sps) {
                if (sp.getEstado() != EstadoSubproceso.FINALIZADO) continue;
                finalizados++;
                var res = calificacionService.resumenSubproceso(sp.getId(), gestor);
                filas.add(new FilaCalidad("SUBPROCESO", s.getIdServicio(), sp.getId(), evaluado(s), nombreCliente(s.getCliente()),
                        sp.getTipoProgreso().getNombreProgreso() + " · Finalizado",
                        detalleQueLlego(s.getProceso() != null ? s.getProceso().getNombreProceso() : null, sp.getTipoProgreso().getNombreProgreso(), sps),
                        sp.getUsuarioAsignado() != null ? nombreUsuario(sp.getUsuarioAsignado()) : "Sin asignar", tiempoServicioService.de(s),
                        res.calculado() ? res.nivel() : null, res.calculado() ? res.puntaje() : null, Accion.REVISAR.name(), Accion.REVISAR.etiqueta()));
            }
            var estados = estados(sps);
            boolean soloPublicados = estados.stream().filter(e -> e != EstadoSubproceso.CANCELADO).allMatch(e -> e == EstadoSubproceso.PUBLICADO)
                    && estados.stream().anyMatch(e -> e == EstadoSubproceso.PUBLICADO);
            if (soloPublicados && !emitida(s)) {
                CalificacionRiesgo c = calificacionRepository.findFirstByServicio_IdServicioAndVigenteTrue(s.getIdServicio()).orElse(null);
                filas.add(new FilaCalidad("PASTA", s.getIdServicio(), null, evaluado(s), nombreCliente(s.getCliente()), "Pasta final pendiente",
                        (s.getProceso() != null ? s.getProceso().getNombreProceso() + " · " : "") + "Publicado " + estados.stream().filter(e -> e == EstadoSubproceso.PUBLICADO).count(),
                        "Tú", tiempoServicioService.de(s), c == null ? null : nivelDe(c), c == null ? null : c.getPuntajeFinal(), Accion.EMITIR_PASTA.name(), Accion.EMITIR_PASTA.etiqueta()));
            }
        }
        // El riesgo más alto primero; a igual riesgo, lo que lleva más vencido.
        filas.sort(Comparator.comparingInt((FilaCalidad f) -> -rango(f.riesgoNivel()))
                .thenComparingInt(f -> f.tiempo() != null && f.tiempo().estadoTiempo() == com.polygraph.erp.shared.enums.EstadoTiempo.VENCIDA ? -f.tiempo().diasRetrasoHabiles() : 1)
                .thenComparing(FilaCalidad::idServicio));
        return new ControlCalidad(finalizados, pastasPendientes, filas.size() > FILAS_CONTROL_CALIDAD ? filas.subList(0, FILAS_CONTROL_CALIDAD) : filas);
    }

    static int rango(String nivel) {
        if (nivel == null) return 0;
        return switch (nivel) { case "ALTO" -> 3; case "MEDIO" -> 2; case "BAJO" -> 1; default -> 0; };
    }

    private static String nivelDe(CalificacionRiesgo c) {
        var n = c.getNivelDecidido() != null ? c.getNivelDecidido() : c.getNivelFinal();
        return n == null ? null : n.name();
    }

    // ───────────────────────── 2. requiere tu atención ─────────────────────────

    private Atencion atencion(Long idGestor, long sinAsignarTotal, Map<Integer, List<ServicioSubproceso>> porServicio, Map<Long, Boolean> completado) {
        var nov = novedadesTableroService.deGestor(idGestor);
        List<Muestra> muestrasNov = nov.masAntiguas().stream().limit(MUESTRAS)
                .map(n -> new Muestra(n.idServicio(), n.idServicioSubproceso(), "#" + n.idServicio() + " " + n.candidato() + " · " + n.tipo())).toList();
        Bloque novedades = new Bloque(nov.abiertas(), nov.abiertasMas48h() == 0 ? "Esperan tu respuesta"
                : (nov.abiertasMas48h() == nov.abiertas() ? "Las " + nov.abiertas() : String.valueOf(nov.abiertasMas48h())) + " llevan más de 48 h", muestrasNov);

        List<Muestra> sinAsignar = new ArrayList<>();
        List<Muestra> sinHoja = new ArrayList<>();
        List<Muestra> citas = new ArrayList<>();
        List<Muestra> devueltos = new ArrayList<>();
        long candidatosSinHoja = 0, citasSinAgendar = 0, cantidadDevueltos = 0;
        for (List<ServicioSubproceso> sps : porServicio.values()) {
            Servicio s = sps.get(0).getServicio();
            List<String> nombresSinAsignar = sps.stream().filter(sp -> sp.getEstado() == EstadoSubproceso.PENDIENTE && !conResponsable(sp))
                    .map(sp -> sp.getTipoProgreso().getNombreProgreso()).toList();
            if (!nombresSinAsignar.isEmpty() && sinAsignar.size() < MUESTRAS) {
                sinAsignar.add(new Muestra(s.getIdServicio(), null, "#" + s.getIdServicio() + " " + evaluado(s) + " · " + String.join(", ", nombresSinAsignar)));
            }
            Boolean hv = s.getCandidato() == null ? null : completado.get(s.getCandidato().getIdCandidato());
            if (Boolean.FALSE.equals(hv)) {
                candidatosSinHoja++;
                if (sinHoja.size() < MUESTRAS) sinHoja.add(new Muestra(s.getIdServicio(), null, "#" + s.getIdServicio() + " " + evaluado(s) + " · " + (s.getProceso() != null ? s.getProceso().getNombreProceso() : "")));
            }
            for (ServicioSubproceso sp : sps) {
                if (Boolean.TRUE.equals(sp.getDevuelto()) && sp.getEstado() == EstadoSubproceso.EN_EJECUCION) {
                    cantidadDevueltos++;
                    if (devueltos.size() < MUESTRAS) devueltos.add(new Muestra(s.getIdServicio(), sp.getId(), "#" + s.getIdServicio() + " " + evaluado(s) + " · "
                            + sp.getTipoProgreso().getNombreProgreso() + " · Devuelto" + (sp.getMotivoDevolucion() == null ? "" : " · " + sp.getMotivoDevolucion())));
                }
                if (esCita(sp) && sp.getEstado() == EstadoSubproceso.PROGRAMANDO) {
                    citasSinAgendar++;
                    if (citas.size() < MUESTRAS) citas.add(new Muestra(s.getIdServicio(), sp.getId(), "#" + s.getIdServicio() + " " + evaluado(s) + " · " + sp.getTipoProgreso().getNombreProgreso()));
                }
            }
        }
        return new Atencion(novedades,
                new Bloque(sinAsignarTotal, "Sin analista, visitador o poligrafista", sinAsignar),
                new Bloque(candidatosSinHoja, "El estudio está en pausa por el candidato", sinHoja),
                new Bloque(citasSinAgendar, "Visitas y poligrafías que el programador aún no agenda", citas),
                new Bloque(cantidadDevueltos, "Esperan que el ejecutor los corrija y los vuelva a enviar", devueltos));
    }

    // ───────────────────────── 3. tiempos ─────────────────────────

    /** Los cinco grupos con las MISMAS especificaciones de las listas, y las vencidas por responsable del retraso. Los filtros son opcionales. */
    @Transactional(readOnly = true)
    public Tiempos tiempos(Long idGestor, Integer idCliente, Integer idProceso, Long idResponsable) {
        Map<Integer, List<ServicioSubproceso>> porServicio = subprocesosDelGestor(idGestor);
        Map<Long, Boolean> completado = hojaVidaCompletada(porServicio.values().stream().map(l -> l.get(0).getServicio()).toList());
        return tiempos(idGestor, idCliente, idProceso, idResponsable, porServicio, completado);
    }

    private Tiempos tiempos(Long idGestor, Integer idCliente, Integer idProceso, Long idResponsable, Map<Integer, List<ServicioSubproceso>> porServicio, Map<Long, Boolean> completado) {
        Map<String, Long> conteo = new LinkedHashMap<>();
        for (BucketTiempo b : BucketTiempo.values()) {
            conteo.put(b.name(), servicioRepository.count(alcance(idGestor, idCliente, idProceso, idResponsable).and(ServicioSpecifications.conBucket(b))));
        }
        Map<String, Long> por = new LinkedHashMap<>();
        for (ResponsableRetraso r : ResponsableRetraso.values()) por.put(r.name(), 0L);
        List<Servicio> vencidas = servicioRepository.findAll(alcance(idGestor, idCliente, idProceso, idResponsable).and(ServicioSpecifications.conBucket(BucketTiempo.VENCIDAS)));
        for (Servicio s : vencidas) {
            List<ServicioSubproceso> sps = porServicio.getOrDefault(s.getIdServicio(), List.of());
            Boolean hv = s.getCandidato() == null ? null : completado.get(s.getCandidato().getIdCandidato());
            por.merge(ProximaAccion.responsableDelRetraso(estados(sps), emitida(s), hv).name(), 1L, Long::sum);
        }
        return new Tiempos(conteo, por);
    }

    static Specification<Servicio> alcance(Long idGestor, Integer idCliente, Integer idProceso, Long idResponsable) {
        return Specification.where(ServicioSpecifications.delGestor(idGestor))
                .and(idCliente == null ? null : ServicioSpecifications.delCliente(idCliente))
                .and(ServicioSpecifications.delProceso(idProceso))
                .and(ServicioSpecifications.conResponsable(idResponsable));
    }

    // ───────────────────────── 4. mi equipo ─────────────────────────

    private List<Persona> equipo(Map<Integer, List<ServicioSubproceso>> porServicio) {
        Map<Long, long[]> cifras = new LinkedHashMap<>();   // asignados, vencidos, en revisión
        Map<Long, Usuario> personas = new LinkedHashMap<>();
        for (List<ServicioSubproceso> sps : porServicio.values()) {
            for (ServicioSubproceso sp : sps) {
                Usuario u = sp.getUsuarioAsignado();
                if (u == null) continue;
                boolean enCurso = sp.getEstado() == EstadoSubproceso.EN_EJECUCION || sp.getEstado() == EstadoSubproceso.REPROGRAMADO;
                boolean enRevision = sp.getEstado() == EstadoSubproceso.FINALIZADO;
                if (!enCurso && !enRevision) continue;
                long[] c = cifras.computeIfAbsent(u.getIdUsuario(), k -> new long[3]);
                personas.putIfAbsent(u.getIdUsuario(), u);
                c[0]++;
                if (sp.getServicio().getEstadoTiempo() == com.polygraph.erp.shared.enums.EstadoTiempo.VENCIDA) c[1]++;
                if (enRevision) c[2]++;
            }
        }
        return cifras.entrySet().stream()
                .map(e -> new Persona(e.getKey(), nombreUsuario(personas.get(e.getKey())), etiquetaRol(personas.get(e.getKey()).getRol()), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .sorted(Comparator.comparingLong(Persona::asignados).reversed().thenComparing(Persona::nombre)).toList();
    }

    private static String etiquetaRol(Rol rol) {
        return switch (rol) {
            case ANALISTA_INTERNO -> "Analista";
            case POLIGRAFISTA -> "Poligrafista";
            case VISITADOR -> "Visitador";
            case PROGRAMADOR -> "Programador";
            default -> rol.name();
        };
    }

    // ───────────────────────── avisar al programador ─────────────────────────

    /**
     * «Avisar al programador»: le manda una notificación con la lista de las citas del gestor que aún no agenda y deja registro (quién, cuándo y
     * cuáles). Con {@code idServicio} avisa solo de ese servicio. Se agrupa por programador: cada uno recibe UN aviso.
     */
    @Transactional
    public AvisoProgramador avisarProgramador(Long idGestor, Integer idServicio) {
        Usuario gestor = usuarioRepository.findById(idGestor).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        List<ServicioSubproceso> citas = subprocesosDelGestor(idGestor).values().stream().flatMap(List::stream)
                .filter(sp -> esCita(sp) && sp.getEstado() == EstadoSubproceso.PROGRAMANDO && sp.getProgramadorResponsable() != null)
                .filter(sp -> idServicio == null || sp.getServicio().getIdServicio().equals(idServicio)).toList();
        if (citas.isEmpty()) {
            throw new ApiException("No hay citas por agendar con programador encargado" + (idServicio != null ? " en este servicio." : "."), HttpStatus.CONFLICT);
        }
        Map<Usuario, List<ServicioSubproceso>> porProgramador = citas.stream().collect(Collectors.groupingBy(ServicioSubproceso::getProgramadorResponsable, LinkedHashMap::new, Collectors.toList()));
        for (var e : porProgramador.entrySet()) {
            String lista = e.getValue().stream().map(sp -> "#" + sp.getServicio().getIdServicio() + " " + evaluado(sp.getServicio()) + " (" + sp.getTipoProgreso().getNombreProgreso() + ")")
                    .collect(Collectors.joining("; "));
            notificacionService.crearParaUsuario(e.getKey().getIdUsuario(), "CITAS_SIN_AGENDAR", "Citas por agendar",
                    nombreUsuario(gestor) + " te recuerda " + e.getValue().size() + (e.getValue().size() == 1 ? " cita pendiente" : " citas pendientes") + " de agendar: " + lista + ".");
            jdbc.update("INSERT INTO avisos_programador (id_gestor, id_programador, cantidad, subprocesos, fecha) VALUES (?,?,?,?,?)",
                    idGestor, e.getKey().getIdUsuario(), e.getValue().size(), e.getValue().stream().map(sp -> String.valueOf(sp.getId())).collect(Collectors.joining(",")),
                    Timestamp.valueOf(LocalDateTime.now()));
        }
        log.info("Gestor {} avisó a {} programador(es) de {} cita(s) sin agendar", idGestor, porProgramador.size(), citas.size());
        return new AvisoProgramador(porProgramador.size(), citas.size());
    }

    // ───────────────────────── opciones de los filtros de Solicitudes ─────────────────────────

    public record Opcion(Object id, String nombre, String detalle) {}

    public record OpcionesFiltro(List<Opcion> clientes, List<Opcion> estudios, List<Opcion> responsables) {}

    /** Lo que ofrecen los selectores Cliente, Estudio y Responsable de Solicitudes: solo los clientes del gestor y su gente. */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public OpcionesFiltro opcionesFiltro(Long idGestor) {
        List<Opcion> clientes = ((List<Cliente>) em.createQuery("SELECT c FROM Cliente c WHERE c.idGestor = :g").setParameter("g", idGestor).getResultList())
                .stream().map(c -> new Opcion(c.getIdCliente(), nombreCliente(c), null)).sorted(Comparator.comparing(Opcion::nombre, String.CASE_INSENSITIVE_ORDER)).toList();
        List<Opcion> estudios = ((List<Object[]>) em.createQuery("SELECT DISTINCT p.idProceso, p.nombreProceso FROM Servicio s JOIN s.proceso p WHERE s.cliente.idGestor = :g ORDER BY p.nombreProceso")
                .setParameter("g", idGestor).getResultList()).stream().map(f -> new Opcion(f[0], (String) f[1], null)).toList();
        Map<Long, Opcion> gente = new LinkedHashMap<>();
        for (String campo : List.of("usuarioAsignado", "programadorResponsable")) {
            for (Usuario u : (List<Usuario>) em.createQuery("SELECT DISTINCT u FROM ServicioSubproceso sp JOIN sp." + campo + " u WHERE sp.servicio.cliente.idGestor = :g")
                    .setParameter("g", idGestor).getResultList()) {
                gente.putIfAbsent(u.getIdUsuario(), new Opcion(u.getIdUsuario(), nombreUsuario(u), etiquetaRol(u.getRol())));
            }
        }
        List<Opcion> responsables = gente.values().stream().sorted(Comparator.comparing(Opcion::nombre, String.CASE_INSENSITIVE_ORDER)).toList();
        return new OpcionesFiltro(clientes, estudios, responsables);
    }

    // ───────────────────────── apoyo ─────────────────────────

    /** Los subprocesos de las solicitudes no canceladas del gestor, agrupados por servicio, con todo lo que las filas necesitan ya cargado. */
    @SuppressWarnings("unchecked")
    Map<Integer, List<ServicioSubproceso>> subprocesosDelGestor(Long idGestor) {
        List<ServicioSubproceso> lista = em.createQuery("SELECT sp FROM ServicioSubproceso sp JOIN FETCH sp.servicio s JOIN FETCH s.cliente JOIN FETCH sp.tipoProgreso "
                        + "LEFT JOIN FETCH sp.usuarioAsignado LEFT JOIN FETCH sp.programadorResponsable LEFT JOIN FETCH s.candidato LEFT JOIN FETCH s.proceso "
                        + "WHERE s.cliente.idGestor = :g AND s.estado <> :cancelado ORDER BY s.idServicio, sp.tipoProgreso.orden")
                .setParameter("g", idGestor).setParameter("cancelado", EstadoServicio.CANCELADO).getResultList();
        return lista.stream().collect(Collectors.groupingBy(sp -> sp.getServicio().getIdServicio(), LinkedHashMap::new, Collectors.toList()));
    }

    /**
     * Por candidato: true = ya envió su hoja de vida, false = se le espera. PC-03: un candidato que nunca abrió el enlace no tiene hoja de vida, pero si el reloj
     * de su servicio está en pausa por él, también se le espera (misma regla que la «próxima acción» de Solicitudes).
     */
    private Map<Long, Boolean> hojaVidaCompletada(List<Servicio> servicios) {
        List<Long> ids = servicios.stream().map(Servicio::getCandidato).filter(java.util.Objects::nonNull).map(c -> c.getIdCandidato()).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, Boolean> r = new java.util.HashMap<>(hojaVidaRepository.findByCandidato_IdCandidatoIn(ids).stream()
                .collect(Collectors.toMap(hv -> hv.getCandidato().getIdCandidato(), HojaVidaEvaluado::getCompletado, (a, b) -> a)));
        for (Servicio s : servicios) {
            if (s.getCandidato() == null) continue;
            boolean pausaPorCandidato = s.getEstadoTiempo() == com.polygraph.erp.shared.enums.EstadoTiempo.EN_PAUSA && s.getPausaOrigen() == com.polygraph.erp.shared.enums.OrigenPausa.CANDIDATO;
            Boolean efectivo = ProximaAccion.esperandoAlCandidato(r.get(s.getCandidato().getIdCandidato()), pausaPorCandidato);
            if (efectivo != null) r.put(s.getCandidato().getIdCandidato(), efectivo);
        }
        return r;
    }

    private static List<EstadoSubproceso> estados(List<ServicioSubproceso> sps) {
        return sps.stream().map(ServicioSubproceso::getEstado).toList();
    }

    private static boolean emitida(Servicio s) {
        return s.getPastaEstado() == PastaEstado.EMITIDA;
    }

    static boolean esCita(ServicioSubproceso sp) {
        Rol r = sp.getTipoProgreso().getRolResponsable();
        return r == Rol.POLIGRAFISTA || r == Rol.VISITADOR;
    }

    /** Responsable de un subproceso: el ejecutor, o —en una cita— el programador encargado (la misma regla de «Sin asignar» de PB-01). */
    static boolean conResponsable(ServicioSubproceso sp) {
        return esCita(sp) ? sp.getProgramadorResponsable() != null : sp.getUsuarioAsignado() != null;
    }

    /** Lo que ProximaAccion necesita saber de un subproceso. */
    static Sub sub(ServicioSubproceso sp) {
        return new Sub(sp.getEstado(), esCita(sp), conResponsable(sp));
    }

    /**
     * Segunda línea de «Qué llegó» (PB-H-15): solo lo que la primera («Antecedentes · Finalizado») no dice. El estudio al que pertenece, si se llama
     * distinto del subproceso, y cómo van los demás subprocesos cuando el estudio tiene más de uno. Si no queda nada, null y la pantalla no pinta
     * la línea — antes salía «Antecedentes · Finalizado 1» debajo de «Antecedentes · Finalizado».
     */
    static String detalleQueLlego(String proceso, String subproceso, List<ServicioSubproceso> sps) {
        List<String> partes = new ArrayList<>();
        if (proceso != null && !proceso.isBlank() && !proceso.trim().equalsIgnoreCase(subproceso == null ? "" : subproceso.trim())) partes.add(proceso.trim());
        if (sps.stream().filter(x -> x.getEstado() != EstadoSubproceso.CANCELADO).count() > 1) partes.add(resumenTexto(sps));
        return partes.isEmpty() ? null : String.join(" · ", partes);
    }

    private static String resumenTexto(List<ServicioSubproceso> sps) {
        return ResumenEstado.de(estados(sps)).stream().map(r -> r.nombre() + " " + r.cantidad()).collect(Collectors.joining(" · "));
    }

    private static String evaluado(Servicio s) {
        var c = s.getCandidato();
        if (c == null) return "—";
        return ((c.getNombres() != null ? c.getNombres() : "") + " " + (c.getApellidos() != null ? c.getApellidos() : "")).trim();
    }

    private static String nombreUsuario(Usuario u) {
        return ((u.getNombre() != null ? u.getNombre() : "") + (u.getApellido() != null ? " " + u.getApellido() : "")).trim();
    }

    static String nombreCliente(Cliente c) {
        if (c == null) return "—";
        if (c.getNombreComercial() != null && !c.getNombreComercial().isBlank()) return c.getNombreComercial();
        if (c.getRazonSocial() != null && !c.getRazonSocial().isBlank()) return c.getRazonSocial();
        return ((c.getNombre() != null ? c.getNombre() : "") + (c.getApellido() != null ? " " + c.getApellido() : "")).trim();
    }
}
