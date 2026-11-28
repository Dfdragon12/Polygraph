package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.shared.enums.Cumplimiento;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * El resumen ÚNICO del tiempo: lo usan todos los tableros (cliente, gestor, administrador, Semáforo). Cuenta lo que guardó SemaforoTiempoService;
 * no calcula fechas. Devuelve los conteos por estado de tiempo, la matriz etapa × estado de tiempo, el cumplimiento del periodo y las más atrasadas.
 *
 * Filtros: cliente, gestor y rango de fechas. El rango se aplica a la fecha de la SOLICITUD en los conteos, la matriz y las más atrasadas, y a la
 * fecha de ENTREGA REAL en el cumplimiento (el periodo en que se publicó). Sin rango, cuenta todo.
 */
@Service
@RequiredArgsConstructor
public class ResumenTiempoService {

    /** Etapas "en curso": lo que aún se está trabajando (Finalizado ya terminó y falta publicarlo; Publicado y Cancelado están cerrados). */
    public static final List<EstadoServicio> EN_CURSO = List.of(
            EstadoServicio.PENDIENTE, EstadoServicio.PROGRAMANDO, EstadoServicio.EN_EJECUCION, EstadoServicio.REPROGRAMADO);

    static final int TOP_POR_DEFECTO = 10;
    static final int TOP_MAXIMO = 50;

    @PersistenceContext
    private EntityManager em;

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final com.polygraph.erp.shared.service.TiempoServicioService tiempoServicioService;

    public record Filtro(Integer idCliente, Long idGestor, LocalDate desde, LocalDate hasta) {
        public static final Filtro TODO = new Filtro(null, null, null, null);
    }

    /** Una fila de la matriz: una etapa y cuántas solicitudes hay en cada estado de tiempo (null = cancelada, sin estado). */
    public record FilaMatriz(String etapa, Map<String, Long> porEstadoTiempo, long sinEstadoTiempo, long total) {}

    /** Publicadas del periodo y cuántas salieron a tiempo o tarde. porcentajeATiempo: 0–100, null si no hubo publicadas. */
    public record CumplimientoPeriodo(long publicadas, long aTiempo, long tarde, Double porcentajeATiempo) {}

    public record Atrasada(Integer idServicio, String evaluado, String cargo, String proceso, Integer idCliente, String cliente, String gestor, String etapa,
                           int diasRetrasoHabiles, LocalDate fechaCompromiso, LocalDate fechaCompromisoAjustada) {}

    public record Resumen(Filtro filtro, long total, long enCurso, Map<String, Long> porEtapa, Map<String, Long> porEstadoTiempo,
                          long sinEstadoTiempo, List<FilaMatriz> matriz, CumplimientoPeriodo cumplimiento, List<Atrasada> masAtrasadas,
                          LocalDateTime generadoEn) {

        public long vencidas() {
            return porEstadoTiempo.getOrDefault(EstadoTiempo.VENCIDA.name(), 0L);
        }

        public long porVencer() {
            return porEstadoTiempo.getOrDefault(EstadoTiempo.POR_VENCER.name(), 0L);
        }
    }

    // ───────────────────────── con autorización ─────────────────────────

    /** El resumen que puede ver ese usuario. Un cliente solo ve SUS solicitudes; un gestor, las de SUS clientes; el administrador, lo que pida. */
    @Transactional(readOnly = true)
    public Resumen resumirPara(String email, Integer idCliente, Long idGestor, LocalDate desde, LocalDate hasta, Integer top) {
        Filtro filtro = filtroPara(email, idCliente, idGestor, desde, hasta);
        int limite = top == null ? TOP_POR_DEFECTO : Math.max(1, Math.min(TOP_MAXIMO, top));
        return resumir(filtro, limite);
    }

    /** Una fila de la lista de solicitudes de un tablero (al hacer clic en una tarjeta o una celda de la matriz). */
    public record FilaSolicitud(Integer idServicio, String evaluado, String cargo, String proceso, Integer idCliente, String cliente, String gestor,
                                String etapa, com.polygraph.erp.shared.dto.TiempoServicio tiempo) {}

    /** Las solicitudes detrás de un número del resumen: mismos filtros y misma autorización. etapa y estadosTiempo son opcionales; la lista sale de
     *  la más atrasada a la menos. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<FilaSolicitud> listarPara(String email, Integer idCliente, Long idGestor, LocalDate desde, LocalDate hasta,
                                                                          EstadoServicio etapa, List<EstadoTiempo> estadosTiempo,
                                                                          org.springframework.data.domain.Pageable pageable) {
        Filtro f = filtroPara(email, idCliente, idGestor, desde, hasta);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Servicio> q = cb.createQuery(Servicio.class);
        Root<Servicio> s = q.from(Servicio.class);
        List<Predicate> p = predicados(cb, s, f, true);
        if (etapa != null) p.add(cb.equal(s.get("estado"), etapa));
        if (estadosTiempo != null && !estadosTiempo.isEmpty()) p.add(s.get("estadoTiempo").in(estadosTiempo));
        q.select(s).where(p.toArray(new Predicate[0]))
                .orderBy(cb.desc(s.get("diasRetrasoHabiles")), cb.asc(s.get("fechaCompromisoAjustada")), cb.asc(s.get("idServicio")));
        CriteriaQuery<Long> contar = cb.createQuery(Long.class);
        Root<Servicio> sc = contar.from(Servicio.class);
        List<Predicate> pc = predicados(cb, sc, f, true);
        if (etapa != null) pc.add(cb.equal(sc.get("estado"), etapa));
        if (estadosTiempo != null && !estadosTiempo.isEmpty()) pc.add(sc.get("estadoTiempo").in(estadosTiempo));
        long total = em.createQuery(contar.select(cb.count(sc)).where(pc.toArray(new Predicate[0]))).getSingleResult();
        List<Servicio> pagina = em.createQuery(q).setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize()).getResultList();
        Map<Long, String> gestores = nombresDeGestores(pagina);
        List<FilaSolicitud> filas = pagina.stream().map(x -> new FilaSolicitud(x.getIdServicio(), evaluado(x), x.getCargo(),
                x.getProceso() == null ? null : x.getProceso().getNombreProceso(), x.getCliente().getIdCliente(), nombreCliente(x.getCliente()),
                gestores.get(x.getCliente().getIdGestor()), x.getEstado().name(), tiempoServicioService.de(x))).toList();
        return new org.springframework.data.domain.PageImpl<>(filas, pageable, total);
    }

    /** Quién puede pedir qué: devuelve el filtro efectivo o lanza 403. */
    private Filtro filtroPara(String email, Integer idCliente, Long idGestor, LocalDate desde, LocalDate hasta) {
        Usuario u = usuarioRepository.findByEmail(email).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ApiException("La fecha inicial no puede ser posterior a la final.", HttpStatus.BAD_REQUEST);
        }
        switch (u.getRol()) {
            case ADMIN_CLIENTE, ANALISTA_CLIENTE -> {
                Integer propio = u.getIdCliente();
                if (propio == null) throw new ApiException("Tu usuario no está asociado a ningún cliente.", HttpStatus.FORBIDDEN);
                if ((idCliente != null && !propio.equals(idCliente)) || idGestor != null) {
                    throw new ApiException("Solo puedes ver el resumen de tus propias solicitudes.", HttpStatus.FORBIDDEN);
                }
                return new Filtro(propio, null, desde, hasta);
            }
            case GESTOR -> {
                if (idGestor != null && !idGestor.equals(u.getIdUsuario())) {
                    throw new ApiException("Solo puedes ver el resumen de tus propios clientes.", HttpStatus.FORBIDDEN);
                }
                if (idCliente != null) {
                    Cliente c = clienteRepository.findById(idCliente).orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
                    if (!Objects.equals(c.getIdGestor(), u.getIdUsuario())) {
                        throw new ApiException("Ese cliente no es de los que gestionas.", HttpStatus.FORBIDDEN);
                    }
                }
                return new Filtro(idCliente, u.getIdUsuario(), desde, hasta);
            }
            case ADMIN_POLYGRAPH -> {
                return new Filtro(idCliente, idGestor, desde, hasta);
            }
            default -> throw new ApiException("Tu rol no puede ver este resumen.", HttpStatus.FORBIDDEN);
        }
    }

    // ───────────────────────── el cálculo (sin autorización: lo usan los tableros ya autorizados) ─────────────────────────

    @Transactional(readOnly = true)
    public Resumen resumir(Filtro f, int top) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // 1. etapa × estado de tiempo
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Servicio> s = q.from(Servicio.class);
        q.multiselect(s.get("estado").alias("etapa"), s.get("estadoTiempo").alias("tiempo"), cb.count(s).alias("n"))
                .where(predicados(cb, s, f, true).toArray(new Predicate[0]))
                .groupBy(s.get("estado"), s.get("estadoTiempo"));
        Map<EstadoServicio, Map<EstadoTiempo, Long>> celdas = new EnumMap<>(EstadoServicio.class);
        Map<EstadoServicio, Long> sinEstadoPorEtapa = new EnumMap<>(EstadoServicio.class);
        for (Tuple t : em.createQuery(q).getResultList()) {
            EstadoServicio etapa = t.get("etapa", EstadoServicio.class);
            EstadoTiempo tiempo = t.get("tiempo", EstadoTiempo.class);
            long n = t.get("n", Long.class);
            if (tiempo == null) sinEstadoPorEtapa.merge(etapa, n, Long::sum);
            else celdas.computeIfAbsent(etapa, k -> new EnumMap<>(EstadoTiempo.class)).merge(tiempo, n, Long::sum);
        }

        Map<String, Long> porEtapa = new LinkedHashMap<>();
        Map<String, Long> porEstadoTiempo = new LinkedHashMap<>();
        for (EstadoTiempo e : EstadoTiempo.values()) porEstadoTiempo.put(e.name(), 0L);
        List<FilaMatriz> matriz = new ArrayList<>();
        long total = 0, sinEstadoTotal = 0, enCurso = 0;
        for (EstadoServicio etapa : EstadoServicio.values()) {
            Map<String, Long> fila = new LinkedHashMap<>();
            long totalFila = 0;
            for (EstadoTiempo e : EstadoTiempo.values()) {
                long n = celdas.getOrDefault(etapa, Map.of()).getOrDefault(e, 0L);
                fila.put(e.name(), n);
                porEstadoTiempo.merge(e.name(), n, Long::sum);
                totalFila += n;
            }
            long sin = sinEstadoPorEtapa.getOrDefault(etapa, 0L);
            totalFila += sin;
            sinEstadoTotal += sin;
            porEtapa.put(etapa.name(), totalFila);
            matriz.add(new FilaMatriz(etapa.name(), fila, sin, totalFila));
            total += totalFila;
            if (EN_CURSO.contains(etapa)) enCurso += totalFila;
        }

        return new Resumen(f, total, enCurso, porEtapa, porEstadoTiempo, sinEstadoTotal, matriz, cumplimiento(cb, f), masAtrasadas(cb, f, top),
                LocalDateTime.now());
    }

    /** Publicadas del periodo (por fecha de entrega real) y cuántas salieron a tiempo. */
    private CumplimientoPeriodo cumplimiento(CriteriaBuilder cb, Filtro f) {
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Servicio> s = q.from(Servicio.class);
        List<Predicate> p = predicados(cb, s, f, false);
        p.add(cb.equal(s.get("estado"), EstadoServicio.PUBLICADO));
        if (f.desde() != null) p.add(cb.greaterThanOrEqualTo(s.<LocalDate>get("fechaEntregaReal"), f.desde()));
        if (f.hasta() != null) p.add(cb.lessThanOrEqualTo(s.<LocalDate>get("fechaEntregaReal"), f.hasta()));
        q.multiselect(s.get("cumplimiento").alias("c"), cb.count(s).alias("n")).where(p.toArray(new Predicate[0])).groupBy(s.get("cumplimiento"));
        long aTiempo = 0, tarde = 0, publicadas = 0;
        for (Tuple t : em.createQuery(q).getResultList()) {
            long n = t.get("n", Long.class);
            publicadas += n;
            if (t.get("c", Cumplimiento.class) == Cumplimiento.TARDE) tarde += n;
            else aTiempo += n;
        }
        Double porcentaje = publicadas == 0 ? null : Math.round(aTiempo * 1000.0 / publicadas) / 10.0;
        return new CumplimientoPeriodo(publicadas, aTiempo, tarde, porcentaje);
    }

    /** Las vencidas con más días hábiles de retraso. */
    private List<Atrasada> masAtrasadas(CriteriaBuilder cb, Filtro f, int top) {
        if (top <= 0) return List.of();
        CriteriaQuery<Servicio> q = cb.createQuery(Servicio.class);
        Root<Servicio> s = q.from(Servicio.class);
        List<Predicate> p = predicados(cb, s, f, true);
        p.add(cb.equal(s.get("estadoTiempo"), EstadoTiempo.VENCIDA));
        q.select(s).where(p.toArray(new Predicate[0]))
                .orderBy(cb.desc(s.get("diasRetrasoHabiles")), cb.asc(s.get("fechaCompromisoAjustada")), cb.asc(s.get("idServicio")));
        List<Atrasada> r = new ArrayList<>();
        List<Servicio> encontradas = em.createQuery(q).setMaxResults(top).getResultList();
        Map<Long, String> gestores = nombresDeGestores(encontradas);
        for (Servicio x : encontradas) {
            r.add(new Atrasada(x.getIdServicio(), evaluado(x), x.getCargo(),
                    x.getProceso() == null ? null : x.getProceso().getNombreProceso(), x.getCliente().getIdCliente(), nombreCliente(x.getCliente()),
                    gestores.get(x.getCliente().getIdGestor()), x.getEstado().name(), x.getDiasRetrasoHabiles() == null ? 0 : x.getDiasRetrasoHabiles(), x.getFechaEntregaEstimada(),
                    x.getFechaCompromisoAjustada()));
        }
        return r;
    }

    private static String evaluado(Servicio x) {
        var cand = x.getCandidato();
        if (cand == null) return null;
        return ((cand.getNombres() == null ? "" : cand.getNombres()) + " " + (cand.getApellidos() == null ? "" : cand.getApellidos())).trim();
    }

    private Map<Long, String> nombresDeGestores(List<Servicio> servicios) {
        List<Long> ids = servicios.stream().map(x -> x.getCliente().getIdGestor()).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> nombres = new LinkedHashMap<>();
        if (ids.isEmpty()) return nombres;
        for (Usuario g : usuarioRepository.findAllById(ids)) {
            nombres.put(g.getIdUsuario(), ((g.getNombre() == null ? "" : g.getNombre()) + " " + (g.getApellido() == null ? "" : g.getApellido())).trim());
        }
        return nombres;
    }

    private List<Predicate> predicados(CriteriaBuilder cb, Root<Servicio> s, Filtro f, boolean rangoPorFechaSolicitud) {
        List<Predicate> p = new ArrayList<>();
        if (f.idCliente() != null) p.add(cb.equal(s.get("cliente").get("idCliente"), f.idCliente()));
        if (f.idGestor() != null) p.add(cb.equal(s.get("cliente").get("idGestor"), f.idGestor()));
        if (rangoPorFechaSolicitud) {
            if (f.desde() != null) p.add(cb.greaterThanOrEqualTo(s.<LocalDate>get("fechaSolicitud"), f.desde()));
            if (f.hasta() != null) p.add(cb.lessThanOrEqualTo(s.<LocalDate>get("fechaSolicitud"), f.hasta()));
        }
        return p;
    }

    private static String nombreCliente(Cliente c) {
        if (c.getNombreComercial() != null && !c.getNombreComercial().isBlank()) return c.getNombreComercial();
        if (c.getRazonSocial() != null && !c.getRazonSocial().isBlank()) return c.getRazonSocial();
        String n = ((c.getNombre() == null ? "" : c.getNombre()) + " " + (c.getApellido() == null ? "" : c.getApellido())).trim();
        return n.isEmpty() ? c.getEmailPrincipal() : n;
    }
}
