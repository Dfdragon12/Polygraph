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
    private final com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository subprocesoRepository;
    private final UsuariosIntenosRepository empleadoRepository;
    private final SaldoServicioClienteService saldoServicioClienteService;
    private final com.polygraph.erp.shared.service.TiempoServicioService tiempoServicioService;

    @Transactional(readOnly = true)
    public DashboardClienteResponse obtenerDashboard() {
        Integer idCliente = obtenerIdClienteActual();
        log.info("Cargando dashboard para idCliente={}", idCliente);

        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));

        List<EstadoServicio> estadosActivos = List.of(
                EstadoServicio.PENDIENTE, EstadoServicio.PROGRAMANDO, EstadoServicio.EN_EJECUCION);

        long serviciosActivos = servicioRepository
                .countByCliente_IdClienteAndEstadoIn(idCliente, estadosActivos);
        long serviciosPendientes = servicioRepository
                .countByCliente_IdClienteAndEstado(idCliente, EstadoServicio.PENDIENTE);

        // AJ-02: "Publicados este mes" = informes publicados en el mes (por fecha de publicación, hora de Colombia), no solicitudes hechas en el mes.
        LocalDate hoyBogota = LocalDate.now(java.time.ZoneId.of("America/Bogota"));
        long publicadosMes = subprocesoRepository.contarPublicadosDeClienteEntre(idCliente,
                hoyBogota.withDayOfMonth(1).atStartOfDay(), hoyBogota.withDayOfMonth(1).plusMonths(1).atStartOfDay());

        log.info("Dashboard idCliente={}: activos={}, pendientes={}, publicadosMes={}",
                idCliente, serviciosActivos, serviciosPendientes, publicadosMes);

        List<Servicio> ultimas = servicioRepository
                .findByCliente_IdClienteOrderByFechaSolicitudDescHoraSolicitudDesc(idCliente, PageRequest.of(0, 5))
                .toList();
        var subprocesos = subprocesosPorServicio(ultimas);
        List<SolicitudResumenResponse> ultimasSolicitudes = ultimas.stream()
                .map(s -> toResumen(s, subprocesos.getOrDefault(s.getIdServicio(), List.of())))
                .toList();

        // Suma de cantidadDisponible de todos los procesos de la bolsa prepago — antes esto
        // quedaba hardcodeado en 0, así que la tarjeta "Disponibles en bolsa" del dashboard nunca
        // reflejaba el saldo real (solo se veía correcto en Comprar Servicios / Mi Crédito).
        long serviciosDisponibles = saldoServicioClienteService.totalDisponible(idCliente);

        return new DashboardClienteResponse(
                serviciosActivos, serviciosPendientes, publicadosMes, serviciosDisponibles, ultimasSolicitudes,
                cliente.getTipoCliente().name(),
                servicioRepository.countByCliente_IdClienteAndEstadoTiempo(idCliente, com.polygraph.erp.shared.enums.EstadoTiempo.VENCIDA));
    }

    @Transactional(readOnly = true)
    public Page<SolicitudResumenResponse> listarSolicitudes(EstadoServicio estado, Pageable pageable) {
        return listarSolicitudes(estado, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SolicitudResumenResponse> listarSolicitudes(EstadoServicio estado, java.util.List<com.polygraph.erp.shared.enums.EstadoTiempo> tiempo,
                                                            String orden, Pageable pageable) {
        Integer idCliente = obtenerIdClienteActual();
        var filtro = org.springframework.data.jpa.domain.Specification
                .where(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.delCliente(idCliente))
                .and(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.conSubprocesoEn(estado == null ? null : com.polygraph.erp.shared.enums.EstadoSubproceso.valueOf(estado.name())))
                .and(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.conTiempo(
                        tiempo));
        Page<Servicio> pagina = servicioRepository.findAll(filtro, org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.orden(orden)));
        var subprocesos = subprocesosPorServicio(pagina.getContent());
        return pagina.map(s -> toResumen(s, subprocesos.getOrDefault(s.getIdServicio(), List.of())));
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

    /** Los subprocesos de las solicitudes de la página, en una sola consulta: de ahí sale el estado que ve el cliente (el de cada subproceso). */
    private java.util.Map<Integer, List<com.polygraph.erp.modules.servicios.entity.ServicioSubproceso>> subprocesosPorServicio(List<Servicio> servicios) {
        if (servicios.isEmpty()) return java.util.Map.of();
        return subprocesoRepository
                .findByServicio_IdServicioInOrderByServicio_IdServicioAscTipoProgreso_OrdenAsc(servicios.stream().map(Servicio::getIdServicio).toList())
                .stream().collect(java.util.stream.Collectors.groupingBy(sp -> sp.getServicio().getIdServicio()));
    }

    private SolicitudResumenResponse toResumen(Servicio s, List<com.polygraph.erp.modules.servicios.entity.ServicioSubproceso> sps) {
        var estados = sps.stream().map(com.polygraph.erp.modules.servicios.entity.ServicioSubproceso::getEstado).toList();
        var avance = com.polygraph.erp.shared.estados.SemaforoEstados.avance(estados);
        return new SolicitudResumenResponse(
                s.getIdServicio(),
                s.getCandidato() != null ? s.getCandidato().getCedula()     : null,
                s.getCandidato() != null ? s.getCandidato().getNombres()    : null,
                s.getCandidato() != null ? s.getCandidato().getApellidos() : null,
                s.getCargo(),
                s.getProceso() != null ? s.getProceso().getNombreProceso() : null,
                s.getEstado(),
                s.getFechaSolicitud(),
                s.fechaParaMostrar(),
                tiempoServicioService.de(s),
                com.polygraph.erp.modules.servicios.dto.ServicioSemaforoFila.ResumenEstado.de(estados),
                avance.publicados(), avance.vigentes(),
                com.polygraph.erp.shared.estados.SemaforoEstados.etiqueta(avance).texto()
        );
    }
}
