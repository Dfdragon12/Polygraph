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
import com.polygraph.erp.shared.enums.EstadoAsignacion;
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

    /** Semáforo de Solicitudes: para estos 3 estados de Servicio (los que
     *  AsignacionService.recalcularEstadoServicio deriva de los subprocesos), un servicio entra al
     *  filtro/conteo si tiene al menos un subproceso activo en el "bucket" correspondiente — ver
     *  ServicioRepository.findByCliente_IdGestorAndEstadoOSubprocesoEstadoIn. Los otros 4 estados
     *  (PROGRAMANDO/PUBLICADO/CANCELADO/REPROGRAMADO) son acciones manuales del gestor a nivel
     *  servicio sin equivalente por subproceso, así que siguen usando igualdad simple. */
    private static final Map<EstadoServicio, List<EstadoAsignacion>> ESTADOS_SUBPROCESO_POR_TAB = Map.of(
            EstadoServicio.PENDIENTE,    List.of(EstadoAsignacion.PENDIENTE),
            EstadoServicio.EN_EJECUCION, List.of(EstadoAsignacion.ASIGNADO, EstadoAsignacion.COMPLETADO),
            EstadoServicio.FINALIZADO,   List.of(EstadoAsignacion.PUBLICADO));

    private final ServicioRepository servicioRepository;
    private final ServicioSubprocesoRepository servicioSubprocesoRepository;
    private final LinkCandidatoGestorService linkCandidatoGestorService;
    private final HojaVidaEvaluadoRepository hojaVidaEvaluadoRepository;

    public GestorDashboardResponse obtenerDashboard(Long idGestor) {
        long pendientes  = servicioRepository.countByCliente_IdGestorAndEstado(idGestor, EstadoServicio.PENDIENTE);
        long programando = servicioRepository.countByCliente_IdGestorAndEstado(idGestor, EstadoServicio.PROGRAMANDO);
        long enEjecucion = servicioRepository.countByCliente_IdGestorAndEstado(idGestor, EstadoServicio.EN_EJECUCION);
        long finalizados = servicioRepository.countByCliente_IdGestorAndEstado(idGestor, EstadoServicio.FINALIZADO);
        long publicados  = servicioRepository.countByCliente_IdGestorAndEstado(idGestor, EstadoServicio.PUBLICADO);
        long cancelados  = servicioRepository.countByCliente_IdGestorAndEstado(idGestor, EstadoServicio.CANCELADO);
        long reprogramados = servicioRepository.countByCliente_IdGestorAndEstado(idGestor, EstadoServicio.REPROGRAMADO);
        long total = pendientes + programando + enEjecucion + finalizados + publicados + cancelados + reprogramados;

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

        List<LinkCandidatoResponse> linksPorAtender = linkCandidatoGestorService.obtenerLinksPorAtender(8);

        return new GestorDashboardResponse(
                pendientes, programando, enEjecucion,
                finalizados, publicados, cancelados, reprogramados, total,
                recientes, pendientesPorEntrega, linksPorAtender);
    }

    public ConteoEstadosResponse contarPorEstado(Long idGestor) {
        long pendientes  = contar(idGestor, EstadoServicio.PENDIENTE);
        long programando = contar(idGestor, EstadoServicio.PROGRAMANDO);
        long enEjecucion = contar(idGestor, EstadoServicio.EN_EJECUCION);
        long finalizados = contar(idGestor, EstadoServicio.FINALIZADO);
        long publicados  = contar(idGestor, EstadoServicio.PUBLICADO);
        long cancelados  = contar(idGestor, EstadoServicio.CANCELADO);
        long reprogramados = contar(idGestor, EstadoServicio.REPROGRAMADO);
        long total = pendientes + programando + enEjecucion + finalizados + publicados + cancelados + reprogramados;
        return new ConteoEstadosResponse(pendientes, programando, enEjecucion, finalizados, publicados, cancelados, reprogramados, total);
    }

    /** Cuenta servicios de un gestor en un estado del semáforo — con el criterio "OR subproceso"
     *  para los 3 estados de ESTADOS_SUBPROCESO_POR_TAB, igualdad simple para el resto. */
    private long contar(Long idGestor, EstadoServicio estado) {
        List<EstadoAsignacion> bucket = ESTADOS_SUBPROCESO_POR_TAB.get(estado);
        return bucket == null
                ? servicioRepository.countByCliente_IdGestorAndEstado(idGestor, estado)
                : servicioRepository.countDistinctByCliente_IdGestorAndEstadoOSubprocesoEstadoIn(idGestor, estado, bucket);
    }

    public Page<SolicitudGestorResponse> listarSolicitudes(Long idGestor, String estado, Pageable pageable) {
        Page<Servicio> pagina;
        if (estado != null && !estado.isBlank()) {
            EstadoServicio estadoEnum = EstadoServicio.valueOf(estado.toUpperCase());
            List<EstadoAsignacion> bucket = ESTADOS_SUBPROCESO_POR_TAB.get(estadoEnum);
            pagina = bucket == null
                    ? servicioRepository.findByCliente_IdGestorAndEstadoOrderByFechaSolicitudDescHoraSolicitudDesc(
                            idGestor, estadoEnum, pageable)
                    : servicioRepository.findByCliente_IdGestorAndEstadoOSubprocesoEstadoIn(
                            idGestor, estadoEnum, bucket, pageable);
        } else {
            pagina = servicioRepository.findByCliente_IdGestorOrderByFechaSolicitudDescHoraSolicitudDesc(idGestor, pageable);
        }

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

        return pagina.map(s -> {
            List<SolicitudGestorResponse.SubprocesoResumen> subprocesos = subprocesosPorServicio
                    .getOrDefault(s.getIdServicio(), List.of()).stream()
                    .map(sp -> new SolicitudGestorResponse.SubprocesoResumen(
                            sp.getTipoProgreso().getNombreProgreso(), sp.getEstado().name(),
                            sp.getProgramadorResponsable() != null))
                    .toList();

            Boolean candidatoCompletado = s.getCandidato() != null
                    ? completadoPorCandidato.get(s.getCandidato().getIdCandidato())
                    : null;

            return new SolicitudGestorResponse(
                    s.getIdServicio(),
                    s.getCandidato() != null ? s.getCandidato().getCedula()     : null,
                    s.getCandidato() != null ? s.getCandidato().getNombres()    : null,
                    s.getCandidato() != null ? s.getCandidato().getApellidos() : null,
                    s.getCargo(),
                    s.getEstado() != null ? s.getEstado().name() : null,
                    s.getFechaSolicitud(), s.getFechaEntregaEstimada(),
                    s.getProceso() != null ? s.getProceso().getNombreProceso() : null,
                    subprocesos,
                    candidatoCompletado);
        });
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
                s.getFechaEntregaEstimada(),
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
