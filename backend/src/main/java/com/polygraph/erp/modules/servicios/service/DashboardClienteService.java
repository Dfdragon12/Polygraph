package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.servicios.dto.DashboardClienteResponse;
import com.polygraph.erp.modules.servicios.dto.SolicitudResumenResponse;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
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
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardClienteService {

    private final ServicioRepository servicioRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public DashboardClienteResponse obtenerDashboard() {
        Integer idCliente = obtenerIdClienteActual();

        List<EstadoServicio> estadosActivos = List.of(
                EstadoServicio.PENDIENTE, EstadoServicio.PROGRAMANDO, EstadoServicio.EN_EJECUCION);
        List<EstadoServicio> estadosFinalizados = List.of(
                EstadoServicio.FINALIZADO, EstadoServicio.PUBLICADO);

        long serviciosActivos = servicioRepository
                .countByCliente_IdClienteAndEstadoIn(idCliente, estadosActivos);
        long serviciosPendientes = servicioRepository
                .countByCliente_IdClienteAndEstado(idCliente, EstadoServicio.PENDIENTE);

        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDate finMes = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        long finalizadosMes = servicioRepository
                .countByCliente_IdClienteAndEstadoInAndFechaSolicitudBetween(
                        idCliente, estadosFinalizados, inicioMes, finMes);

        List<SolicitudResumenResponse> ultimasSolicitudes = servicioRepository
                .findByCliente_IdClienteOrderByFechaSolicitudDescHoraSolicitudDesc(
                        idCliente, PageRequest.of(0, 5))
                .map(this::toResumen)
                .toList();

        return new DashboardClienteResponse(
                serviciosActivos, serviciosPendientes, finalizadosMes, 0, ultimasSolicitudes);
    }

    @Transactional(readOnly = true)
    public Page<SolicitudResumenResponse> listarSolicitudes(EstadoServicio estado, Pageable pageable) {
        Integer idCliente = obtenerIdClienteActual();
        Page<Servicio> pagina = estado != null
                ? servicioRepository.findByCliente_IdClienteAndEstadoOrderByFechaSolicitudDescHoraSolicitudDesc(
                        idCliente, estado, pageable)
                : servicioRepository.findByCliente_IdClienteOrderByFechaSolicitudDescHoraSolicitudDesc(
                        idCliente, pageable);
        return pagina.map(this::toResumen);
    }

    private Integer obtenerIdClienteActual() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (usuario.getIdCliente() == null) {
            throw new ApiException("Usuario no asociado a ningún cliente", HttpStatus.FORBIDDEN);
        }
        return usuario.getIdCliente();
    }

    private SolicitudResumenResponse toResumen(Servicio s) {
        String nombreCandidato = s.getCandidato() != null
                ? s.getCandidato().getNombres() + " " + s.getCandidato().getApellidos()
                : null;
        return new SolicitudResumenResponse(
                s.getIdServicio(),
                s.getProceso().getNombreProceso(),
                s.getEstado(),
                s.getFechaSolicitud(),
                s.getFechaEntregaEstudio(),
                nombreCandidato
        );
    }
}
