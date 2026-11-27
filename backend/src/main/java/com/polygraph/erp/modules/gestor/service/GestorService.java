package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.gestor.dto.ConteoEstadosResponse;
import com.polygraph.erp.modules.gestor.dto.GestorDashboardResponse;
import com.polygraph.erp.modules.gestor.dto.LinkCandidatoResponse;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudGestorResponse;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoServicio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GestorService {

    private static final List<EstadoServicio> ESTADOS_ACTIVOS = List.of(
            EstadoServicio.PENDIENTE, EstadoServicio.PROGRAMANDO,
            EstadoServicio.EN_EJECUCION, EstadoServicio.REPROGRAMADO);

    private final ServicioRepository servicioRepository;
    private final ServicioSubprocesoRepository servicioSubprocesoRepository;
    private final LinkCandidatoGestorService linkCandidatoGestorService;
    private final HojaVidaEvaluadoRepository hojaVidaEvaluadoRepository;
    private final NovedadesTableroService novedadesTableroService;
    private final com.polygraph.erp.shared.service.TiempoServicioService tiempoServicioService;
    private final com.polygraph.erp.modules.riesgo.repository.CalificacionRiesgoRepository calificacionRiesgoRepository;
    private final com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository linkCandidatoRepository;
    private final com.polygraph.erp.modules.solicitudes.repository.DocumentoEstudioRepository documentoEstudioRepository;

    public GestorDashboardResponse obtenerDashboard(Long idGestor) {
        long pendientes  = contar(idGestor, EstadoServicio.PENDIENTE);
        long programando = contar(idGestor, EstadoServicio.PROGRAMANDO);
        long enEjecucion = contar(idGestor, EstadoServicio.EN_EJECUCION);
        long finalizados = contar(idGestor, EstadoServicio.FINALIZADO);
        long publicados  = contar(idGestor, EstadoServicio.PUBLICADO);
        long cancelados  = contar(idGestor, EstadoServicio.CANCELADO);
        long reprogramados = contar(idGestor, EstadoServicio.REPROGRAMADO);
        long total = totalServicios(idGestor);

        List<GestorDashboardResponse.SolicitudResumen> recientes =
                servicioRepository.findTop8ByCliente_IdGestorOrderByFechaSolicitudDescHoraSolicitudDesc(idGestor).stream()
                        .map(this::toResumen)
                        .toList();

        List<GestorDashboardResponse.SolicitudResumen> pendientesPorEntrega =
                servicioRepository.findByCliente_IdGestorAndEstadoInOrderByFechaEntregaEstimadaAsc(
                                idGestor, ESTADOS_ACTIVOS, PageRequest.of(0, 10))
                        .stream()
                        .map(this::toResumen)
                        .toList();

        List<LinkCandidatoResponse> linksPorAtender = linkCandidatoGestorService.obtenerLinksPorAtender(idGestor, 8);

        return new GestorDashboardResponse(
                pendientes, programando, enEjecucion,
                finalizados, publicados, cancelados, reprogramados, total,
                recientes, pendientesPorEntrega, linksPorAtender);
    }

    /** Datos de las gráficas del tablero: conteos agrupados en la BD, nada de listas. "Esta semana"
     *  = de pasado mañana a 7 días; "después" = más de 7 días. */
    public com.polygraph.erp.modules.gestor.dto.GraficasGestorResponse obtenerGraficas(Long idGestor) {
        java.time.LocalDate hoy = java.time.LocalDate.now();

        // Solicitudes por semana (12 semanas, empezando en lunes)
        java.time.LocalDate lunesActual = hoy.with(java.time.DayOfWeek.MONDAY);
        java.time.LocalDate desde = lunesActual.minusWeeks(11);
        Map<java.time.LocalDate, Long> porSemana = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) porSemana.put(desde.plusWeeks(i), 0L);
        for (Object[] fila : servicioRepository.contarPorFechaSolicitud(idGestor, desde)) {
            if (fila[0] == null) continue;
            java.time.LocalDate lunes = ((java.time.LocalDate) fila[0]).with(java.time.DayOfWeek.MONDAY);
            porSemana.merge(lunes, (Long) fila[1], Long::sum);
        }
        List<com.polygraph.erp.modules.gestor.dto.GraficasGestorResponse.Semana> semanas = porSemana.entrySet().stream()
                .map(e -> new com.polygraph.erp.modules.gestor.dto.GraficasGestorResponse.Semana(e.getKey(), e.getValue()))
                .toList();

        // Entregas de los servicios en curso
        long vencidas = 0, deHoy = 0, manana = 0, semana = 0, despues = 0, sinFecha = 0;
        // Abiertas = todo lo que no está publicado ni cancelado (incluye FINALIZADO: falta publicarlo al cliente). El estado de tiempo (vencida,
        // en pausa...) sale de lo guardado; solo se agrupa por la fecha de compromiso ajustada para ver cuándo vencen las que vienen.
        List<EstadoServicio> abiertas = java.util.Arrays.stream(EstadoServicio.values()).filter(e -> !e.cerrado()).toList();
        for (Object[] fila : servicioRepository.contarPorFechaEntrega(idGestor, abiertas)) {
            long n = (Long) fila[2];
            com.polygraph.erp.shared.enums.EstadoTiempo estadoTiempo = (com.polygraph.erp.shared.enums.EstadoTiempo) fila[1];
            if (estadoTiempo == com.polygraph.erp.shared.enums.EstadoTiempo.VENCIDA) { vencidas += n; continue; }
            if (estadoTiempo == com.polygraph.erp.shared.enums.EstadoTiempo.EN_PAUSA) continue;   // el reloj no corre: no hay fecha que vigilar
            if (fila[0] == null) { sinFecha += n; continue; }
            long dias = java.time.temporal.ChronoUnit.DAYS.between(hoy, (java.time.LocalDate) fila[0]);
            if (dias <= 0) deHoy += n;                       // vence hoy (o ayer sin día hábil de por medio: fin de semana o festivo)
            else if (dias == 1) manana += n;
            else if (dias <= 7) semana += n;
            else despues += n;
        }

        PageRequest top = PageRequest.of(0, 6);
        List<com.polygraph.erp.modules.gestor.dto.GraficasGestorResponse.Conteo> porCliente =
                servicioRepository.contarPorCliente(idGestor, ESTADOS_ACTIVOS, top).stream()
                        .map(f -> new com.polygraph.erp.modules.gestor.dto.GraficasGestorResponse.Conteo(
                                nombreCliente((String) f[1], (String) f[2], (String) f[3], (String) f[4]), (Long) f[5]))
                        .toList();
        List<com.polygraph.erp.modules.gestor.dto.GraficasGestorResponse.Conteo> porProceso =
                servicioRepository.contarPorProceso(idGestor, ESTADOS_ACTIVOS, top).stream()
                        .map(f -> new com.polygraph.erp.modules.gestor.dto.GraficasGestorResponse.Conteo((String) f[0], (Long) f[1]))
                        .toList();

        return new com.polygraph.erp.modules.gestor.dto.GraficasGestorResponse(
                semanas,
                new com.polygraph.erp.modules.gestor.dto.GraficasGestorResponse.Entregas(vencidas, deHoy, manana, semana, despues, sinFecha),
                porCliente, porProceso, novedadesTableroService.deGestor(idGestor));
    }

    private static String nombreCliente(String razonSocial, String nombreComercial, String nombre, String apellido) {
        if (nombreComercial != null && !nombreComercial.isBlank()) return nombreComercial;
        if (razonSocial != null && !razonSocial.isBlank()) return razonSocial;
        return ((nombre != null ? nombre : "") + (apellido != null ? " " + apellido : "")).trim();
    }

    public ConteoEstadosResponse contarPorEstado(Long idGestor) {
        long pendientes  = contar(idGestor, EstadoServicio.PENDIENTE);
        long programando = contar(idGestor, EstadoServicio.PROGRAMANDO);
        long enEjecucion = contar(idGestor, EstadoServicio.EN_EJECUCION);
        long finalizados = contar(idGestor, EstadoServicio.FINALIZADO);
        long publicados  = contar(idGestor, EstadoServicio.PUBLICADO);
        long cancelados  = contar(idGestor, EstadoServicio.CANCELADO);
        long reprogramados = contar(idGestor, EstadoServicio.REPROGRAMADO);
        long total = totalServicios(idGestor);
        long vencidas = servicioRepository.countByCliente_IdGestorAndEstadoTiempo(idGestor, com.polygraph.erp.shared.enums.EstadoTiempo.VENCIDA);
        return new ConteoEstadosResponse(pendientes, programando, enEjecucion, finalizados, publicados, cancelados, reprogramados, total, vencidas);
    }

    /** Cuenta servicios de un gestor en un estado del semáforo: los que tienen al menos un subproceso en él (un servicio puede contar en varios). */
    private long contar(Long idGestor, EstadoServicio estado) {
        return servicioRepository.count(org.springframework.data.jpa.domain.Specification
                .where(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.delGestor(idGestor))
                .and(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.conSubprocesoEn(EstadoSubproceso.valueOf(estado.name()))));
    }

    /** El total es la cantidad de servicios, no la suma de los estados (un servicio cuenta en cada estado donde tenga un subproceso). */
    private long totalServicios(Long idGestor) {
        return servicioRepository.count(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.delGestor(idGestor));
    }

    public Page<SolicitudGestorResponse> listarSolicitudes(Long idGestor, String estado, Pageable pageable) {
        return listarSolicitudes(idGestor, estado, null, pageable);
    }

    /** q: texto del buscador de la tabla — con texto, filtra en el servidor sobre TODAS las
     *  solicitudes del gestor (antes la tabla solo filtraba la página que tenía cargada). */
    public Page<SolicitudGestorResponse> listarSolicitudes(Long idGestor, String estado, String q, Pageable pageable) {
        return listarSolicitudes(idGestor, estado, q, null, null, pageable);
    }

    /** tiempo: filtra por estado de tiempo (vencidas, por vencer...); orden "retraso": lo más atrasado primero. Se combinan con el buscador. */
    public Page<SolicitudGestorResponse> listarSolicitudes(Long idGestor, String estado, String q, java.util.List<com.polygraph.erp.shared.enums.EstadoTiempo> tiempo,
                                                           String orden, Pageable pageable) {
        return listarSolicitudes(idGestor, new FiltrosSolicitudes(estado, q, tiempo, null, null, null, null, orden), pageable);
    }

    /**
     * PB-05: todos los filtros de la pantalla Solicitudes, combinables: tarjeta de estado (uno o varios, separados por coma), grupo de tiempo
     * (Vencidas · Vence hoy · Vence mañana · A tiempo · En pausa), cliente, estudio, responsable y buscador. NO hay filtro por riesgo.
     */
    public record FiltrosSolicitudes(String estado, String q, java.util.List<com.polygraph.erp.shared.enums.EstadoTiempo> estadoTiempo,
                                     com.polygraph.erp.shared.estados.BucketTiempo tiempo, Integer idCliente, Integer idProceso, Long idResponsable, String orden) {}

    public Page<SolicitudGestorResponse> listarSolicitudes(Long idGestor, FiltrosSolicitudes f, Pageable pageable) {
        String orden = f.orden();
        // El semáforo es un filtro (SE-04/SE-09): el servicio entra al estado si al menos uno de sus subprocesos está en él.
        var filtro = org.springframework.data.jpa.domain.Specification
                .where(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.delGestor(idGestor))
                .and(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.conSubprocesoEnAlguno(
                        com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.estadosDeFiltro(f.estado())))
                .and(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.conTiempo(f.estadoTiempo()))
                .and(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.conBucket(f.tiempo()))
                .and(f.idCliente() == null ? null : com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.delCliente(f.idCliente()))
                .and(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.delProceso(f.idProceso()))
                .and(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.conResponsable(f.idResponsable()))
                .and(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.buscar(f.q()))
                .and(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.ordenadoPorRetraso(orden));
        Page<Servicio> pagina = servicioRepository.findAll(filtro, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.orden(orden)));

        List<Integer> idsServicio = pagina.getContent().stream().map(Servicio::getIdServicio).toList();
        Map<Integer, List<ServicioSubproceso>> subprocesosPorServicio = servicioSubprocesoRepository
                .findByServicio_IdServicioInOrderByServicio_IdServicioAscTipoProgreso_OrdenAsc(idsServicio)
                .stream()
                .collect(Collectors.groupingBy(sp -> sp.getServicio().getIdServicio(), LinkedHashMap::new, Collectors.toList()));

        List<Long> idsCandidato = pagina.getContent().stream()
                .map(Servicio::getCandidato).filter(java.util.Objects::nonNull)
                .map(c -> c.getIdCandidato()).toList();
        Map<Long, Boolean> completadoPorCandidato = hojaVidaEvaluadoRepository
                .findByCandidato_IdCandidatoIn(idsCandidato).stream()
                .collect(Collectors.toMap(hv -> hv.getCandidato().getIdCandidato(), HojaVidaEvaluado::getCompletado));

        Map<Integer, com.polygraph.erp.modules.riesgo.entity.CalificacionRiesgo> riesgoPorServicio = idsServicio.isEmpty() ? Map.of()
                : calificacionRiesgoRepository.findByVigenteTrueAndServicio_IdServicioIn(idsServicio).stream()
                        .collect(Collectors.toMap(c -> c.getServicio().getIdServicio(), c -> c, (a, b) -> a));
        Map<Integer, Boolean> enlaceEnviado = idsServicio.isEmpty() ? Map.of()
                : linkCandidatoRepository.findByServicio_IdServicioIn(idsServicio).stream()
                        .collect(Collectors.toMap(l -> l.getServicio().getIdServicio(), l -> l.getEnviadoEn() != null, (a, b) -> a || b));

        Map<Integer, com.polygraph.erp.modules.solicitudes.entity.DocumentoEstudio> pdfPorServicio = idsServicio.isEmpty() ? Map.of()
                : documentoEstudioRepository.findByIdServicioInAndVigenteTrue(idsServicio).stream()
                        .collect(Collectors.toMap(d -> d.getIdServicio(), d -> d, (a, b) -> a.getVersion() >= b.getVersion() ? a : b));

        return pagina.map(s -> {
            List<SolicitudGestorResponse.SubprocesoResumen> subprocesos = subprocesosPorServicio
                    .getOrDefault(s.getIdServicio(), List.of()).stream()
                    .map(sp -> new SolicitudGestorResponse.SubprocesoResumen(
                            sp.getTipoProgreso().getNombreProgreso(), sp.getEstado().name(),
                            sp.getProgramadorResponsable() != null))
                    .toList();

            Boolean candidatoCompletado = s.getCandidato() != null
                    ? com.polygraph.erp.shared.estados.ProximaAccion.esperandoAlCandidato(completadoPorCandidato.get(s.getCandidato().getIdCandidato()),
                            s.getEstadoTiempo() == com.polygraph.erp.shared.enums.EstadoTiempo.EN_PAUSA && s.getPausaOrigen() == com.polygraph.erp.shared.enums.OrigenPausa.CANDIDATO)
                    : null;
            var proxima = com.polygraph.erp.shared.estados.ProximaAccion.de(s.getEstado() == EstadoServicio.CANCELADO,
                    subprocesosPorServicio.getOrDefault(s.getIdServicio(), List.of()).stream().map(BandejaGestorService::sub).toList(),
                    s.getPastaEstado() == com.polygraph.erp.shared.enums.PastaEstado.EMITIDA, candidatoCompletado,
                    enlaceEnviado.getOrDefault(s.getIdServicio(), false));

            return new SolicitudGestorResponse(
                    s.getIdServicio(),
                    s.getCandidato() != null ? s.getCandidato().getCedula()     : null,
                    s.getCandidato() != null ? s.getCandidato().getNombres()    : null,
                    s.getCandidato() != null ? s.getCandidato().getApellidos() : null,
                    s.getCargo(),
                    s.getEstado() != null ? s.getEstado().name() : null,
                    s.getFechaSolicitud(), s.fechaParaMostrar(),
                    s.getProceso() != null ? s.getProceso().getNombreProceso() : null,
                    subprocesos,
                    candidatoCompletado,
                    tiempoServicioService.de(s),
                    avance(subprocesosPorServicio.getOrDefault(s.getIdServicio(), List.of())).publicados(),
                    avance(subprocesosPorServicio.getOrDefault(s.getIdServicio(), List.of())).vigentes(),
                    etiquetaPublicacion(subprocesosPorServicio.getOrDefault(s.getIdServicio(), List.of()), s),
                    com.polygraph.erp.modules.servicios.dto.ServicioSemaforoFila.ResumenEstado.de(
                            subprocesosPorServicio.getOrDefault(s.getIdServicio(), List.of()).stream().map(ServicioSubproceso::getEstado).toList()),
                    s.getCliente() != null ? s.getCliente().getIdCliente() : null,
                    BandejaGestorService.nombreCliente(s.getCliente()),
                    riesgoVisible(riesgoPorServicio.get(s.getIdServicio())) == null ? null : nivelDe(riesgoPorServicio.get(s.getIdServicio())),
                    riesgoVisible(riesgoPorServicio.get(s.getIdServicio())) == null ? null : riesgoPorServicio.get(s.getIdServicio()).getPuntajeFinal(),
                    riesgoVisible(riesgoPorServicio.get(s.getIdServicio())) != null
                            && riesgoPorServicio.get(s.getIdServicio()).getTipoAjuste() == com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.TipoAjuste.CRITICO,
                    s.getPastaEstado() != null ? s.getPastaEstado().name() : null,
                    proxima.name(), proxima.etiqueta(),
                    enlaceEnviado.getOrDefault(s.getIdServicio(), false),
                    pdfPorServicio.get(s.getIdServicio()) == null ? null : pdfPorServicio.get(s.getIdServicio()).getEstado(),
                    pdfPorServicio.get(s.getIdServicio()) == null ? null : pdfPorServicio.get(s.getIdServicio()).getVersion());
        });
    }

    /** Solo un cálculo terminado se muestra: nunca «BAJO 0» de un estudio que aún no se calcula (SE-06). */
    private static com.polygraph.erp.modules.riesgo.entity.CalificacionRiesgo riesgoVisible(com.polygraph.erp.modules.riesgo.entity.CalificacionRiesgo c) {
        return c != null && c.getEstado() == com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.EstadoResultado.CALCULADO ? c : null;
    }

    /** El nivel que vale: el que puso el gestor o, si no cambió nada, el del motor. */
    private static String nivelDe(com.polygraph.erp.modules.riesgo.entity.CalificacionRiesgo c) {
        var n = c.getNivelDecidido() != null ? c.getNivelDecidido() : c.getNivelFinal();
        return n == null ? null : n.name();
    }

    private static com.polygraph.erp.shared.estados.SemaforoEstados.Avance avance(List<ServicioSubproceso> sps) {
        return com.polygraph.erp.shared.estados.SemaforoEstados.avance(sps.stream().map(ServicioSubproceso::getEstado).toList());
    }

    /** «Publicado parcial» / «Estudio completo publicado», o null si ninguno está publicado. */
    private static String etiquetaPublicacion(List<ServicioSubproceso> sps, Servicio s) {
        return com.polygraph.erp.shared.estados.SemaforoEstados.etiqueta(avance(sps), s.getPastaEstado() == com.polygraph.erp.shared.enums.PastaEstado.EMITIDA).texto();
    }

    private GestorDashboardResponse.SolicitudResumen toResumen(Servicio s) {
        String nombreCliente = resolverNombreCliente(s.getCliente());
        return new GestorDashboardResponse.SolicitudResumen(
                s.getIdServicio(),
                s.getCandidato() != null ? s.getCandidato().getCedula()     : null,
                s.getCandidato() != null ? s.getCandidato().getNombres()    : null,
                s.getCandidato() != null ? s.getCandidato().getApellidos() : null,
                s.getCargo(),
                s.getEstado() != null ? s.getEstado().name() : null,
                s.getFechaSolicitud(),
                s.fechaParaMostrar(),
                nombreCliente,
                s.getProceso() != null ? s.getProceso().getNombreProceso() : null);
    }

    private String resolverNombreCliente(Cliente c) {
        if (c == null) return "—";
        if (c.getRazonSocial() != null) return c.getRazonSocial();
        String nombre = c.getNombre() != null ? c.getNombre() : "";
        String apellido = c.getApellido() != null ? " " + c.getApellido() : "";
        return nombre + apellido;
    }
}
