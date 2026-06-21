package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.servicios.dto.DashboardClienteResponse;
import com.polygraph.erp.modules.servicios.dto.SolicitudResumenResponse;
import com.polygraph.erp.modules.solicitudes.entity.Solicitud;
import com.polygraph.erp.modules.solicitudes.repository.SolicitudRepository;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardClienteService {

    private final SolicitudRepository solicitudRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public DashboardClienteResponse obtenerDashboard() {
        Integer idCliente = obtenerIdClienteActual();
        log.info("Cargando dashboard para idCliente={}", idCliente);

        List<EstadoServicio> estadosActivos = List.of(
                EstadoServicio.PENDIENTE, EstadoServicio.PROGRAMANDO, EstadoServicio.EN_EJECUCION);
        List<EstadoServicio> estadosFinalizados = List.of(
                EstadoServicio.FINALIZADO, EstadoServicio.PUBLICADO);

        long serviciosActivos = solicitudRepository
                .countByCliente_IdClienteAndEstadoIn(idCliente, estadosActivos);
        long serviciosPendientes = solicitudRepository
                .countByCliente_IdClienteAndEstado(idCliente, EstadoServicio.PENDIENTE);

        LocalDateTime inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime finMes = LocalDate.now()
                .withDayOfMonth(LocalDate.now().lengthOfMonth())
                .atTime(23, 59, 59);
        long finalizadosMes = solicitudRepository
                .countByCliente_IdClienteAndEstadoInAndFechaSolicitudBetween(
                        idCliente, estadosFinalizados, inicioMes, finMes);

        log.info("Dashboard idCliente={}: activos={}, pendientes={}, finalizadosMes={}",
                idCliente, serviciosActivos, serviciosPendientes, finalizadosMes);

        List<SolicitudResumenResponse> ultimasSolicitudes = solicitudRepository
                .findByCliente_IdClienteOrderByFechaSolicitudDesc(
                        idCliente, PageRequest.of(0, 5))
                .map(this::toResumen)
                .toList();

        return new DashboardClienteResponse(
                serviciosActivos, serviciosPendientes, finalizadosMes, 0, ultimasSolicitudes);
    }

    @Transactional(readOnly = true)
    public Page<SolicitudResumenResponse> listarSolicitudes(EstadoServicio estado, Pageable pageable) {
        Integer idCliente = obtenerIdClienteActual();
        Page<Solicitud> pagina = estado != null
                ? solicitudRepository.findByCliente_IdClienteAndEstadoOrderByFechaSolicitudDesc(
                        idCliente, estado, pageable)
                : solicitudRepository.findByCliente_IdClienteOrderByFechaSolicitudDesc(
                        idCliente, pageable);
        return pagina.map(this::toResumen);
    }

    private Integer obtenerIdClienteActual() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        log.debug("Resolviendo idCliente para email={}", email);
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (usuario.getIdCliente() == null) {
            throw new ApiException("Usuario no asociado a ningún cliente", HttpStatus.FORBIDDEN);
        }
        log.debug("idCliente resuelto={}", usuario.getIdCliente());
        return usuario.getIdCliente();
    }

    private SolicitudResumenResponse toResumen(Solicitud s) {
        List<String> servicios = s.getServicios().stream()
                .map(ss -> ss.getCatalogoServicio().getNombre())
                .toList();
        return new SolicitudResumenResponse(
                s.getIdSolicitud(),
                s.getCedulaEvaluado(),
                s.getNombresEvaluado(),
                s.getApellidosEvaluado(),
                s.getCargo(),
                s.getCiudadEvaluado(),
                servicios,
                s.getEstado(),
                s.getFechaSolicitud(),
                s.getFechaEntregaEstimada()
        );
    }
}
