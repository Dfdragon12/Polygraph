package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.pagos.dto.SaldoServicioResponse;
import com.polygraph.erp.modules.pagos.service.SaldoServicioClienteService;
import com.polygraph.erp.modules.servicios.dto.DashboardClienteResponse;
import com.polygraph.erp.modules.servicios.dto.GestorContactoResponse;
import com.polygraph.erp.modules.servicios.dto.SolicitudResumenResponse;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.usuarios.entity.UsuariosInternos;
import com.polygraph.erp.modules.usuarios.repository.UsuariosIntenosRepository;
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
    private final ClienteRepository clienteRepository;
    private final UsuariosIntenosRepository empleadoRepository;
    private final SaldoServicioClienteService saldoServicioClienteService;

    @Transactional(readOnly = true)
    public DashboardClienteResponse obtenerDashboard() {
        Integer idCliente = obtenerIdClienteActual();
        log.info("Cargando dashboard para idCliente={}", idCliente);

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

        log.info("Dashboard idCliente={}: activos={}, pendientes={}, finalizadosMes={}",
                idCliente, serviciosActivos, serviciosPendientes, finalizadosMes);

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

    @Transactional(readOnly = true)
    public List<SaldoServicioResponse> obtenerBolsaServicios() {
        return saldoServicioClienteService.obtenerSaldos(obtenerIdClienteActual());
    }

    @Transactional(readOnly = true)
    public GestorContactoResponse obtenerGestor() {
        Integer idCliente = obtenerIdClienteActual();
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        if (cliente.getIdGestor() == null) {
            return new GestorContactoResponse(null, null, null);
        }
        Usuario gestor = usuarioRepository.findById(cliente.getIdGestor())
                .orElseThrow(() -> new ApiException("Gestor no encontrado", HttpStatus.NOT_FOUND));
        String telefono = gestor.getIdEmpleado() != null
                ? empleadoRepository.findById(gestor.getIdEmpleado())
                        .map(UsuariosInternos::getTelefono)
                        .orElse(null)
                : null;
        String nombreCompleto = (gestor.getNombre() != null ? gestor.getNombre() : "")
                + (gestor.getApellido() != null ? " " + gestor.getApellido() : "");
        return new GestorContactoResponse(nombreCompleto.trim(), telefono, gestor.getEmail());
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

    private SolicitudResumenResponse toResumen(Servicio s) {
        return new SolicitudResumenResponse(
                s.getIdServicio(),
                s.getCandidato() != null ? s.getCandidato().getCedula()     : null,
                s.getCandidato() != null ? s.getCandidato().getNombres()    : null,
                s.getCandidato() != null ? s.getCandidato().getApellidos() : null,
                s.getCargo(),
                s.getProceso() != null ? s.getProceso().getNombreProceso() : null,
                s.getEstado(),
                s.getFechaSolicitud(),
                s.getFechaEntregaEstimada()
        );
    }
}
