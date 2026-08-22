package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.pagos.service.SaldoServicioClienteService;
import com.polygraph.erp.shared.enums.EstadoMora;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.enums.TipoPersona;
import com.polygraph.erp.modules.catalogo.entity.ProcesoTipoProgreso;
import com.polygraph.erp.modules.catalogo.repository.ProcesoTipoProgresoRepository;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.repository.CandidatoRepository;
import com.polygraph.erp.modules.gestor.service.AsignacionService;
import com.polygraph.erp.modules.servicios.entity.HistorialEstadoServicio;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.ReversionSolicitud;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.HistorialEstadoServicioRepository;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.ReversionSolicitudRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.solicitudes.dto.*;
import com.polygraph.erp.shared.entity.Notificacion;
import com.polygraph.erp.shared.enums.EstadoAprobacion;
import com.polygraph.erp.shared.enums.EstadoAsignacion;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import com.polygraph.erp.shared.service.DiasHabilesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SolicitudService {

    private final ServicioRepository servicioRepository;
    private final HistorialEstadoServicioRepository historialRepository;
    private final CandidatoRepository candidatoRepository;
    private final ProcesoRepository procesoRepository;
    private final ClienteRepository clienteRepository;
    private final ClientePospagoRepository clientePospagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final LinkCandidatoRepository linkCandidatoRepository;
    private final NotificacionRepository notificacionRepository;
    private final ReversionSolicitudRepository reversionSolicitudRepository;
    private final DiasHabilesService diasHabilesService;
    private final ProcesoTipoProgresoRepository procesoTipoProgresoRepository;
    private final ServicioSubprocesoRepository servicioSubprocesoRepository;
    private final AsignacionService asignacionService;
    private final SaldoServicioClienteService saldoServicioClienteService;

    public List<SolicitudDetalleResponse> crearSolicitud(SolicitudRequest request, String emailUsuario) {
        var usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        var cliente = clienteRepository.findById(usuario.getIdCliente())
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));

        if (cliente.getTipoCliente() == TipoCliente.POSPAGO) {
            validarMoraPospago(cliente);
        }

        // Validar catálogo y duplicados
        List<Proceso> procesos = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now();
        LocalDate limiteAntiDuplicado = ahora.toLocalDate().minusMonths(3);

        for (Integer idProceso : request.procesosIds()) {
            Proceso proceso = procesoRepository.findById(idProceso)
                    .orElseThrow(() -> new ApiException(
                            "Proceso de catálogo no encontrado: " + idProceso, HttpStatus.BAD_REQUEST));

            if (!Boolean.TRUE.equals(proceso.getActivo())) {
                throw new ApiException("El proceso '" + proceso.getNombreProceso() + "' no está disponible", HttpStatus.BAD_REQUEST);
            }

            if (servicioRepository.existeDuplicado(request.cedula(), idProceso, limiteAntiDuplicado)) {
                throw new ApiException(
                        "El evaluado con cédula " + request.cedula() +
                        " ya tiene un servicio activo para '" + proceso.getNombreProceso() +
                        "' en los últimos 3 meses", HttpStatus.CONFLICT);
            }

            if (cliente.getTipoCliente() == TipoCliente.PREPAGO) {
                saldoServicioClienteService.validarDisponibilidad(cliente.getIdCliente(), proceso);
            }

            procesos.add(proceso);
        }

        // Reutilizar candidato existente o crear uno nuevo
        Candidato candidato = candidatoRepository.findByCedula(request.cedula())
                .orElseGet(() -> candidatoRepository.save(Candidato.builder()
                        .cedula(request.cedula())
                        .tipoDocumento("CC")
                        .nombres(request.nombres())
                        .apellidos(request.apellidos())
                        .celular(request.celular())
                        .emailPrincipal(request.email())
                        .idCiudadResidencia(request.idCiudad())
                        .build()));

        // Calcular fecha de entrega (máximo de días hábiles de todos los procesos)
        int maxDiasHabiles = procesos.stream()
                .mapToInt(p -> p.getDiasHabilesEntrega() != null ? p.getDiasHabilesEntrega() : 5)
                .max()
                .orElse(5);
        LocalDate fechaEntrega = diasHabilesService.calcularFechaEntrega(ahora, maxDiasHabiles);

        // Crear un servicio por cada proceso elegido
        List<Servicio> serviciosCreados = new ArrayList<>();
        for (Proceso proceso : procesos) {
            Servicio servicio = Servicio.builder()
                    .cliente(cliente)
                    .candidato(candidato)
                    .proceso(proceso)
                    .fechaSolicitud(ahora.toLocalDate())
                    .horaSolicitud(ahora.toLocalTime())
                    .estado(EstadoServicio.PENDIENTE)
                    .cargo(request.cargo())
                    .notas(request.notas())
                    .fechaEntregaEstimada(fechaEntrega)
                    .usuarioSolicita(usuario)
                    .build();
            Servicio guardado = servicioRepository.save(servicio);
            if (cliente.getTipoCliente() == TipoCliente.PREPAGO) {
                saldoServicioClienteService.descontar(cliente.getIdCliente(), proceso);
            }
            serviciosCreados.add(guardado);
            generarSubprocesos(guardado);
        }

        // Generar link para que el evaluado llene su hoja de vida (cubre toda la tanda)
        String tokenLink = UUID.randomUUID().toString().replace("-", "");
        LinkCandidato link = LinkCandidato.builder()
                .servicio(serviciosCreados.get(0))
                .token(tokenLink)
                .fechaCreacion(ahora)
                .fechaExpiracion(ahora.plusHours(36))
                .usado(false)
                .build();
        linkCandidatoRepository.save(link);

        // Notificar gestores
        Integer primerIdServicio = serviciosCreados.get(0).getIdServicio();
        usuarioRepository.findAll().stream()
                .filter(u -> Rol.GESTOR.equals(u.getRol()) && Boolean.TRUE.equals(u.getActivo()))
                .forEach(gestor -> notificacionRepository.save(Notificacion.builder()
                        .usuario(gestor)
                        .tipo("NUEVA_SOLICITUD")
                        .titulo("Nueva solicitud de servicio")
                        .mensaje("El cliente " + nombreCliente(cliente) +
                                 " ha registrado una nueva solicitud para " +
                                 request.nombres() + " " + request.apellidos())
                        .leida(false)
                        .fechaCreacion(ahora)
                        .referenciaTipo("SERVICIO")
                        .referenciaId(primerIdServicio.longValue())
                        .build()));

        // Notificar al usuario cliente
        notificacionRepository.save(Notificacion.builder()
                .usuario(usuario)
                .tipo("SOLICITUD_CREADA")
                .titulo("Solicitud registrada exitosamente")
                .mensaje("Su solicitud para " + request.nombres() + " " + request.apellidos() +
                         " fue registrada. Fecha estimada de entrega: " + fechaEntrega)
                .leida(false)
                .fechaCreacion(ahora)
                .referenciaTipo("SERVICIO")
                .referenciaId(primerIdServicio.longValue())
                .build());

        log.info("Servicios creados: ids={}, cliente={}, evaluado={}",
                serviciosCreados.stream().map(Servicio::getIdServicio).toList(),
                cliente.getIdCliente(), request.cedula());

        List<SolicitudDetalleResponse> respuesta = new ArrayList<>();
        for (int i = 0; i < serviciosCreados.size(); i++) {
            respuesta.add(construirDetalle(serviciosCreados.get(i), i == 0 ? tokenLink : null));
        }
        return respuesta;
    }

    @Transactional(readOnly = true)
    public Page<SolicitudResponse> listarSolicitudes(Integer idCliente, String estado, Pageable pageable) {
        Page<Servicio> pagina;
        if (estado != null && !estado.isBlank()) {
            EstadoServicio estadoEnum = EstadoServicio.valueOf(estado.toUpperCase());
            pagina = servicioRepository.findByCliente_IdClienteAndEstadoOrderByFechaSolicitudDescHoraSolicitudDesc(
                    idCliente, estadoEnum, pageable);
        } else {
            pagina = servicioRepository.findByCliente_IdClienteOrderByFechaSolicitudDescHoraSolicitudDesc(idCliente, pageable);
        }
        return pagina.map(this::construirResumen);
    }

    @Transactional(readOnly = true)
    public SolicitudDetalleResponse obtenerDetalle(Integer idServicio) {
        Servicio servicio = servicioRepository.findById(idServicio)
                .orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
        return construirDetalle(servicio, null);
    }

    public void cambiarEstado(Integer idServicio, CambioEstadoRequest request, UserDetails userDetails) {
        Servicio servicio = servicioRepository.findById(idServicio)
                .orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));

        var usuario = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        EstadoServicio nuevoEstado = EstadoServicio.valueOf(request.estado().toUpperCase());
        EstadoServicio estadoAnterior = servicio.getEstado();
        validarTransicion(usuario.getRol(), estadoAnterior, nuevoEstado);

        servicio.setEstado(nuevoEstado);

        historialRepository.save(HistorialEstadoServicio.builder()
                .servicio(servicio)
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(nuevoEstado)
                .usuario(usuario)
                .fechaCambio(LocalDateTime.now())
                .observacion(request.observacion())
                .build());

        log.info("Estado cambiado: servicio={}, {} → {}, usuario={}",
                idServicio, estadoAnterior, nuevoEstado, userDetails.getUsername());
    }

    /**
     * El GESTOR no puede revertir directamente una solicitud CANCELADA — solo puede
     * solicitarlo. Esto crea un registro PENDIENTE (y una notificación a los
     * ADMIN_POLYGRAPH); el estado de la solicitud no cambia hasta que un
     * administrador la apruebe desde /admin/reversiones.
     */
    public void solicitarReversion(Integer idServicio, CambioEstadoRequest request, UserDetails userDetails) {
        Servicio servicio = servicioRepository.findById(idServicio)
                .orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));

        if (servicio.getEstado() != EstadoServicio.CANCELADO) {
            throw new ApiException("Solo se puede solicitar reversión de solicitudes canceladas", HttpStatus.CONFLICT);
        }

        if (reversionSolicitudRepository.existsByServicio_IdServicioAndEstado(idServicio, EstadoAprobacion.PENDIENTE)) {
            throw new ApiException("Ya existe una solicitud de reversión pendiente para esta solicitud", HttpStatus.CONFLICT);
        }

        EstadoServicio estadoDeseado = EstadoServicio.valueOf(request.estado().toUpperCase());
        if (estadoDeseado != EstadoServicio.PROGRAMANDO && estadoDeseado != EstadoServicio.REPROGRAMADO) {
            throw new ApiException("El estado deseado solo puede ser PROGRAMANDO o REPROGRAMADO", HttpStatus.BAD_REQUEST);
        }

        if (request.observacion() == null || request.observacion().isBlank()) {
            throw new ApiException("Debes indicar el motivo de la solicitud", HttpStatus.BAD_REQUEST);
        }

        var gestor = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        String nombreEvaluado = servicio.getCandidato() != null
                ? servicio.getCandidato().getNombres() + " " + servicio.getCandidato().getApellidos()
                : "el evaluado";
        LocalDateTime ahora = LocalDateTime.now();

        reversionSolicitudRepository.save(ReversionSolicitud.builder()
                .servicio(servicio)
                .estadoDeseado(estadoDeseado)
                .motivo(request.observacion())
                .estado(EstadoAprobacion.PENDIENTE)
                .solicitadoPor(gestor)
                .fechaSolicitud(ahora)
                .build());

        usuarioRepository.findAll().stream()
                .filter(u -> Rol.ADMIN_POLYGRAPH.equals(u.getRol()) && Boolean.TRUE.equals(u.getActivo()))
                .forEach(admin -> notificacionRepository.save(Notificacion.builder()
                        .usuario(admin)
                        .tipo("SOLICITUD_REVERSION")
                        .titulo("Solicitud de reversión de estado")
                        .mensaje(gestor.getNombre() + " solicita revertir la solicitud #" + idServicio +
                                 " (" + nombreEvaluado + ") de CANCELADO a " + estadoDeseado +
                                 ". Motivo: " + request.observacion())
                        .leida(false)
                        .fechaCreacion(ahora)
                        .realizadoPor(gestor)
                        .referenciaTipo("SERVICIO")
                        .referenciaId(idServicio.longValue())
                        .build()));

        log.info("Reversión solicitada: servicio={}, CANCELADO → {}, gestor={}",
                idServicio, estadoDeseado, userDetails.getUsername());
    }

    public BulkUploadResponse procesarCargaMasiva(MultipartFile archivo, String emailUsuario) {
        List<BulkUploadResponse.ErrorFila> errores = new ArrayList<>();
        int exitosas = 0;

        try (Workbook workbook = new XSSFWorkbook(archivo.getInputStream())) {
            Sheet hoja = workbook.getSheetAt(0);
            // Fila 0 = encabezado, empezar desde fila 1
            for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) continue;

                try {
                    String cedula = obtenerCelda(fila, 2);
                    String nombres = obtenerCelda(fila, 3);
                    String apellidos = obtenerCelda(fila, 4);
                    String telefono = obtenerCelda(fila, 5);
                    String cargo = obtenerCelda(fila, 7);
                    String tipoServicio = obtenerCelda(fila, 8);

                    if (cedula.isBlank() || nombres.isBlank() || apellidos.isBlank()) {
                        errores.add(new BulkUploadResponse.ErrorFila(i + 1, cedula,
                                "Cédula, nombres y apellidos son obligatorios"));
                        continue;
                    }

                    // Buscar proceso del catálogo por nombre
                    List<Proceso> coincidencias = procesoRepository.findAll().stream()
                            .filter(p -> p.getNombreProceso().equalsIgnoreCase(tipoServicio.trim()))
                            .toList();

                    if (coincidencias.isEmpty()) {
                        errores.add(new BulkUploadResponse.ErrorFila(i + 1, cedula,
                                "Tipo de servicio no reconocido: " + tipoServicio));
                        continue;
                    }

                    // La ciudad de la plantilla de carga masiva llega como texto libre (columna 6);
                    // no se resuelve a id_ciudad aquí — solo el formulario de Nueva Solicitud lo hace.
                    SolicitudRequest req = new SolicitudRequest(
                            cedula, nombres, apellidos, telefono, null, null, cargo,
                            List.of(coincidencias.get(0).getIdProceso()), null);
                    crearSolicitud(req, emailUsuario);
                    exitosas++;
                } catch (ApiException e) {
                    errores.add(new BulkUploadResponse.ErrorFila(i + 1,
                            obtenerCelda(fila, 2), e.getMessage()));
                } catch (Exception e) {
                    errores.add(new BulkUploadResponse.ErrorFila(i + 1,
                            obtenerCelda(fila, 2), "Error procesando fila: " + e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new ApiException("Error al leer el archivo Excel: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        log.info("Carga masiva: {} exitosas, {} errores", exitosas, errores.size());
        return new BulkUploadResponse(exitosas, errores.size(), errores);
    }

    private void validarTransicion(Rol rol, EstadoServicio actual, EstadoServicio nuevo) {
        boolean permitido = switch (rol) {
            case ADMIN_POLYGRAPH -> true;
            case GESTOR -> actual != EstadoServicio.CANCELADO &&
                           (nuevo == EstadoServicio.PROGRAMANDO ||
                            nuevo == EstadoServicio.PUBLICADO   ||
                            nuevo == EstadoServicio.CANCELADO   ||
                            nuevo == EstadoServicio.REPROGRAMADO);
            case ADMIN_CLIENTE, ANALISTA_CLIENTE -> nuevo == EstadoServicio.CANCELADO;
            case ANALISTA_INTERNO -> nuevo == EstadoServicio.EN_EJECUCION ||
                                     nuevo == EstadoServicio.FINALIZADO ||
                                     nuevo == EstadoServicio.PUBLICADO;
            default -> false;
        };
        if (!permitido) {
            throw new ApiException("No tiene permisos para cambiar al estado " + nuevo, HttpStatus.FORBIDDEN);
        }
    }

    private SolicitudResponse construirResumen(Servicio s) {
        return new SolicitudResponse(
                s.getIdServicio(),
                s.getCandidato() != null ? s.getCandidato().getCedula()     : null,
                s.getCandidato() != null ? s.getCandidato().getNombres()    : null,
                s.getCandidato() != null ? s.getCandidato().getApellidos() : null,
                s.getCargo(),
                s.getEstado() != null ? s.getEstado().name() : null,
                s.getFechaSolicitud(), s.getFechaEntregaEstimada(),
                s.getProceso() != null ? s.getProceso().getNombreProceso() : null);
    }

    private SolicitudDetalleResponse construirDetalle(Servicio s, String tokenLink) {
        List<SolicitudDetalleResponse.HistorialResponse> historialResp = historialRepository
                .findByServicio_IdServicioOrderByFechaCambioDesc(s.getIdServicio()).stream()
                .map(h -> new SolicitudDetalleResponse.HistorialResponse(
                        h.getEstadoAnterior() != null ? h.getEstadoAnterior().name() : null,
                        h.getEstadoNuevo().name(),
                        h.getFechaCambio(),
                        h.getUsuario().getEmail(),
                        h.getObservacion()))
                .toList();

        Cliente cliente = s.getCliente();

        return new SolicitudDetalleResponse(
                s.getIdServicio(),
                s.getCandidato() != null ? s.getCandidato().getCedula()      : null,
                s.getCandidato() != null ? s.getCandidato().getNombres()     : null,
                s.getCandidato() != null ? s.getCandidato().getApellidos()  : null,
                s.getCandidato() != null ? s.getCandidato().getCelular()    : null,
                s.getCandidato() != null ? s.getCandidato().getEmailPrincipal() : null,
                s.getCargo(), s.getNotas(),
                s.getProceso() != null ? s.getProceso().getIdProceso() : null,
                s.getProceso() != null ? s.getProceso().getNombreProceso() : null,
                s.getProceso() != null && s.getProceso().getClasificacion() != null
                        ? s.getProceso().getClasificacion().getNombre() : null,
                s.getEstado() != null ? s.getEstado().name() : null,
                s.getFechaSolicitud(), s.getHoraSolicitud(), s.getFechaEntregaEstimada(),
                tokenLink, historialResp,
                cliente != null ? nombreCliente(cliente)      : null,
                cliente != null ? cliente.getNit()            : null,
                cliente != null ? cliente.getTelefono()       : null,
                cliente != null ? cliente.getEmailPrincipal() : null);
    }

    /** Genera una fila de asignación (PENDIENTE) por cada subproceso habilitado del proceso del servicio. */
    /** Regla de negocio: "Cliente con mora no puede solicitar servicios" — solo aplica a pospago (prepago se limita por saldo). */
    private void validarMoraPospago(Cliente cliente) {
        ClientePospago pospago = clientePospagoRepository.findByIdCliente(cliente.getIdCliente()).orElse(null);
        if (pospago == null || pospago.getEstadoMora() == null || pospago.getEstadoMora() == EstadoMora.NORMAL) {
            return;
        }
        String motivo = pospago.getEstadoMora() == EstadoMora.SUSPENDIDO
                ? "tu cuenta está suspendida por mora"
                : "tu cuenta está en mora";
        throw new ApiException(
                "No puedes solicitar servicios: " + motivo + ". Contacta a tu gestor para regularizar tu situación.",
                HttpStatus.FORBIDDEN);
    }

    private void generarSubprocesos(Servicio servicio) {
        List<ProcesoTipoProgreso> pasos = procesoTipoProgresoRepository
                .findByProceso_IdProcesoOrderByTipoProgreso_OrdenAscTipoProgreso_NombreProgresoAsc(
                        servicio.getProceso().getIdProceso());

        LocalDateTime ahora = LocalDateTime.now();
        for (ProcesoTipoProgreso paso : pasos) {
            if (!Boolean.TRUE.equals(paso.getHabilitado())) continue;
            ServicioSubproceso subproceso = ServicioSubproceso.builder()
                    .servicio(servicio)
                    .tipoProgreso(paso.getTipoProgreso())
                    .estado(EstadoAsignacion.PENDIENTE)
                    .fechaCreacion(ahora)
                    .build();
            // Si ya hay alguien calificado (capacidad marcada en Equipo Polygraph), se asigna
            // solo — el gestor/programador solo interviene para casos puntuales o reasignar.
            asignacionService.autoAsignar(subproceso);
            servicioSubprocesoRepository.save(subproceso);
        }
    }

    private String nombreCliente(Cliente c) {
        if (TipoPersona.JURIDICA.equals(c.getTipoPersona()) && c.getRazonSocial() != null) {
            return c.getRazonSocial();
        }
        String nombre = c.getNombre() != null ? c.getNombre() : "";
        String apellido = c.getApellido() != null ? " " + c.getApellido() : "";
        return (nombre + apellido).trim();
    }

    private String obtenerCelda(Row fila, int columna) {
        Cell celda = fila.getCell(columna);
        if (celda == null) return "";
        return switch (celda.getCellType()) {
            case STRING -> celda.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) celda.getNumericCellValue());
            default -> "";
        };
    }
}
