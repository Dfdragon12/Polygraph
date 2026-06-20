package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.gestor.dto.GestorDashboardResponse;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudResponse;
import com.polygraph.erp.modules.solicitudes.entity.Solicitud;
import com.polygraph.erp.modules.solicitudes.repository.SolicitudRepository;
import com.polygraph.erp.shared.enums.EstadoServicio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GestorService {

    private final SolicitudRepository solicitudRepository;

    public GestorDashboardResponse obtenerDashboard() {
        long pendientes  = solicitudRepository.countByEstado(EstadoServicio.PENDIENTE);
        long programando = solicitudRepository.countByEstado(EstadoServicio.PROGRAMANDO);
        long enEjecucion = solicitudRepository.countByEstado(EstadoServicio.EN_EJECUCION);
        long finalizados = solicitudRepository.countByEstado(EstadoServicio.FINALIZADO);
        long publicados  = solicitudRepository.countByEstado(EstadoServicio.PUBLICADO);
        long cancelados  = solicitudRepository.countByEstado(EstadoServicio.CANCELADO);
        long total = pendientes + programando + enEjecucion + finalizados + publicados + cancelados;

        List<GestorDashboardResponse.SolicitudResumen> recientes =
                solicitudRepository.findTop8ByOrderByFechaSolicitudDesc().stream()
                        .map(this::toResumen)
                        .toList();

        return new GestorDashboardResponse(
                pendientes, programando, enEjecucion,
                finalizados, publicados, cancelados, total, recientes);
    }

    public Page<SolicitudResponse> listarSolicitudes(String estado, Pageable pageable) {
        Page<Solicitud> pagina;
        if (estado != null && !estado.isBlank()) {
            EstadoServicio estadoEnum = EstadoServicio.valueOf(estado.toUpperCase());
            pagina = solicitudRepository.findByEstadoOrderByFechaSolicitudDesc(estadoEnum, pageable);
        } else {
            pagina = solicitudRepository.findAllByOrderByFechaSolicitudDesc(pageable);
        }
        return pagina.map(s -> {
            List<String> servicios = s.getServicios().stream()
                    .map(ss -> ss.getCatalogoServicio().getNombre())
                    .toList();
            return new SolicitudResponse(
                    s.getIdSolicitud(), s.getCedulaEvaluado(), s.getNombresEvaluado(),
                    s.getApellidosEvaluado(), s.getCargo(),
                    s.getEstado() != null ? s.getEstado().name() : null,
                    s.getFechaSolicitud(), s.getFechaEntregaEstimada(), servicios);
        });
    }

    private GestorDashboardResponse.SolicitudResumen toResumen(Solicitud s) {
        String nombreCliente = resolverNombreCliente(s.getCliente());
        List<String> servicios = s.getServicios().stream()
                .map(ss -> ss.getCatalogoServicio().getNombre())
                .toList();
        return new GestorDashboardResponse.SolicitudResumen(
                s.getIdSolicitud(),
                s.getCedulaEvaluado(),
                s.getNombresEvaluado(),
                s.getApellidosEvaluado(),
                s.getCargo(),
                s.getEstado() != null ? s.getEstado().name() : null,
                s.getFechaSolicitud(),
                s.getFechaEntregaEstimada(),
                nombreCliente,
                servicios);
    }

    private String resolverNombreCliente(Cliente c) {
        if (c == null) return "—";
        if (c.getRazonSocial() != null) return c.getRazonSocial();
        String nombre = c.getNombre() != null ? c.getNombre() : "";
        String apellido = c.getApellido() != null ? " " + c.getApellido() : "";
        return nombre + apellido;
    }
}
