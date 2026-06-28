package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.repository.CandidatoRepository;
import com.polygraph.erp.modules.servicios.entity.CatalogoServicio;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.repository.CatalogoServicioRepository;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.modules.solicitudes.dto.*;
import com.polygraph.erp.modules.solicitudes.entity.HistorialSolicitud;
import com.polygraph.erp.modules.solicitudes.entity.Solicitud;
import com.polygraph.erp.modules.solicitudes.entity.SolicitudServicio;
import com.polygraph.erp.modules.solicitudes.repository.HistorialSolicitudRepository;
import com.polygraph.erp.modules.solicitudes.repository.SolicitudRepository;
import com.polygraph.erp.modules.solicitudes.repository.SolicitudServicioRepository;
import com.polygraph.erp.shared.entity.Notificacion;
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

    private final SolicitudRepository solicitudRepository;
    private final SolicitudServicioRepository solicitudServicioRepository;
    private final HistorialSolicitudRepository historialRepository;
    private final CandidatoRepository candidatoRepository;
    private final CatalogoServicioRepository catalogoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final LinkCandidatoRepository linkCandidatoRepository;
    private final NotificacionRepository notificacionRepository;
    private final DiasHabilesService diasHabilesService;

    public SolicitudDetalleResponse crearSolicitud(SolicitudRequest request, String emailUsuario) {
        var usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        var cliente = clienteRepository.findById(usuario.getIdCliente())
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));

        // Validar catálogo y duplicados
        List<CatalogoServicio> catalogos = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limiteAntiDuplicado = ahora.minusMonths(3);

        for (Integer idCatalogo : request.serviciosIds()) {
            CatalogoServicio catalogo = catalogoRepository.findById(idCatalogo)
                    .orElseThrow(() -> new ApiException(
                            "Servicio de catálogo no encontrado: " + idCatalogo, HttpStatus.BAD_REQUEST));

            if (!Boolean.TRUE.equals(catalogo.getActivo())) {
                throw new ApiException("El servicio '" + catalogo.getNombre() + "' no está disponible", HttpStatus.BAD_REQUEST);
            }

            if (solicitudServicioRepository.existeDuplicado(request.cedula(), idCatalogo, limiteAntiDuplicado)) {
                throw new ApiException(
                        "El evaluado con cédula " + request.cedula() +
                        " ya tiene una solicitud activa para '" + catalogo.getNombre() +
                        "' en los últimos 3 meses", HttpStatus.CONFLICT);
            }
            catalogos.add(catalogo);
        }

        // Reutilizar candidato existente o crear referencia
        Optional<Candidato> candidatoExistente = candidatoRepository.findByCedula(request.cedula());

        // Calcular fecha de entrega (máximo de días hábiles de todos los servicios)
        int maxDiasHabiles = catalogos.stream()
                .mapToInt(c -> c.getDiasHabilesEntrega() != null ? c.getDiasHabilesEntrega() : 5)
                .max()
                .orElse(5);
        LocalDate fechaEntrega = diasHabilesService.calcularFechaEntrega(ahora, maxDiasHabiles);

        // Crear solicitud
        Solicitud solicitud = Solicitud.builder()
                .cliente(cliente)
                .candidato(candidatoExistente.orElse(null))
                .cedulaEvaluado(request.cedula())
                .nombresEvaluado(request.nombres())
                .apellidosEvaluado(request.apellidos())
                .celularEvaluado(request.celular())
                .emailEvaluado(request.email())
                .ciudadEvaluado(request.ciudad())
                .cargo(request.cargo())
                .notas(request.notas())
                .estado(EstadoServicio.PENDIENTE)
                .fechaSolicitud(ahora)
                .fechaEntregaEstimada(fechaEntrega)
                .usuarioSolicita(usuario)
                .build();
        solicitudRepository.save(solicitud);

        // Crear items de servicio
        for (CatalogoServicio catalogo : catalogos) {
            SolicitudServicio item = SolicitudServicio.builder()
                    .solicitud(solicitud)
                    .catalogoServicio(catalogo)
                    .estado(EstadoServicio.PENDIENTE)
                    .build();
            solicitud.getServicios().add(item);
        }
        solicitudRepository.save(solicitud);

        // Generar link para evaluado
        String tokenLink = UUID.randomUUID().toString().replace("-", "");
        LinkCandidato link = LinkCandidato.builder()
                .idSolicitud(solicitud.getIdSolicitud())
                .token(tokenLink)
                .fechaCreacion(ahora)
                .fechaExpiracion(ahora.plusHours(36))
                .usado(false)
                .build();
        linkCandidatoRepository.save(link);

        // Notificar gestores
        usuarioRepository.findAll().stream()
                .filter(u -> Rol.GESTOR.equals(u.getRol()) && Boolean.TRUE.equals(u.getActivo()))
                .forEach(gestor -> notificacionRepository.save(Notificacion.builder()
                        .usuario(gestor)
                        .tipo("NUEVA_SOLICITUD")
                        .titulo("Nueva solicitud de servicio")
                        .mensaje("El cliente " + cliente.getNombre() + " " +
                                 (cliente.getApellido() != null ? cliente.getApellido() : "") +
                                 " ha registrado una nueva solicitud para " +
                                 request.nombres() + " " + request.apellidos())
                        .leida(false)
                        .fechaCreacion(ahora)
                        .referenciaTipo("SOLICITUD")
                        .referenciaId(solicitud.getIdSolicitud())
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
                .referenciaTipo("SOLICITUD")
                .referenciaId(solicitud.getIdSolicitud())
                .build());

        log.info("Solicitud creada: id={}, cliente={}, evaluado={}", solicitud.getIdSolicitud(),
                cliente.getIdCliente(), request.cedula());

        return construirDetalle(solicitud, tokenLink);
    }

    @Transactional(readOnly = true)
    public Page<SolicitudResponse> listarSolicitudes(Integer idCliente, String estado, Pageable pageable) {
        Page<Solicitud> pagina;
        if (estado != null && !estado.isBlank()) {
            EstadoServicio estadoEnum = EstadoServicio.valueOf(estado.toUpperCase());
            pagina = solicitudRepository.findByCliente_IdClienteAndEstadoOrderByFechaSolicitudDesc(
                    idCliente, estadoEnum, pageable);
        } else {
            pagina = solicitudRepository.findByCliente_IdClienteOrderByFechaSolicitudDesc(idCliente, pageable);
        }
        return pagina.map(this::construirResumen);
    }

    @Transactional(readOnly = true)
    public SolicitudDetalleResponse obtenerDetalle(Long idSolicitud) {
        Solicitud solicitud = solicitudRepository.findById(idSolicitud)
                .orElseThrow(() -> new ApiException("Solicitud no encontrada", HttpStatus.NOT_FOUND));
        return construirDetalle(solicitud, null);
    }

    public void cambiarEstado(Long idSolicitud, CambioEstadoRequest request, UserDetails userDetails) {
        Solicitud solicitud = solicitudRepository.findById(idSolicitud)
                .orElseThrow(() -> new ApiException("Solicitud no encontrada", HttpStatus.NOT_FOUND));

        var usuario = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        EstadoServicio nuevoEstado = EstadoServicio.valueOf(request.estado().toUpperCase());
        EstadoServicio estadoAnterior = solicitud.getEstado();
        validarTransicion(usuario.getRol(), estadoAnterior, nuevoEstado);

        solicitud.setEstado(nuevoEstado);

        historialRepository.save(HistorialSolicitud.builder()
                .solicitud(solicitud)
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(nuevoEstado)
                .usuario(usuario)
                .fechaCambio(LocalDateTime.now())
                .observacion(request.observacion())
                .build());

        log.info("Estado cambiado: solicitud={}, {} → {}, usuario={}",
                idSolicitud, estadoAnterior, nuevoEstado, userDetails.getUsername());
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
                    String ciudad = obtenerCelda(fila, 6);
                    String cargo = obtenerCelda(fila, 7);
                    String tipoServicio = obtenerCelda(fila, 8);

                    if (cedula.isBlank() || nombres.isBlank() || apellidos.isBlank()) {
                        errores.add(new BulkUploadResponse.ErrorFila(i + 1, cedula,
                                "Cédula, nombres y apellidos son obligatorios"));
                        continue;
                    }

                    // Buscar catálogo por nombre
                    List<CatalogoServicio> coincidencias = catalogoRepository.findAll().stream()
                            .filter(c -> c.getNombre().equalsIgnoreCase(tipoServicio.trim()))
                            .toList();

                    if (coincidencias.isEmpty()) {
                        errores.add(new BulkUploadResponse.ErrorFila(i + 1, cedula,
                                "Tipo de servicio no reconocido: " + tipoServicio));
                        continue;
                    }

                    SolicitudRequest req = new SolicitudRequest(
                            cedula, nombres, apellidos, telefono, null, ciudad, cargo,
                            List.of(coincidencias.get(0).getIdCatalogo()), null);
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
            case GESTOR -> nuevo == EstadoServicio.PROGRAMANDO ||
                           nuevo == EstadoServicio.PUBLICADO   ||
                           nuevo == EstadoServicio.CANCELADO   ||
                           nuevo == EstadoServicio.REPROGRAMADO;
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

    private SolicitudResponse construirResumen(Solicitud s) {
        List<String> servicios = s.getServicios().stream()
                .map(ss -> ss.getCatalogoServicio().getNombre())
                .toList();
        return new SolicitudResponse(
                s.getIdSolicitud(), s.getCedulaEvaluado(), s.getNombresEvaluado(),
                s.getApellidosEvaluado(), s.getCargo(), s.getCiudadEvaluado(),
                s.getEstado() != null ? s.getEstado().name() : null,
                s.getFechaSolicitud(), s.getFechaEntregaEstimada(), servicios);
    }

    private SolicitudDetalleResponse construirDetalle(Solicitud s, String tokenLink) {
        List<SolicitudDetalleResponse.ServicioItemResponse> itemsResp = s.getServicios().stream()
                .map(ss -> new SolicitudDetalleResponse.ServicioItemResponse(
                        ss.getCatalogoServicio().getIdCatalogo(),
                        ss.getCatalogoServicio().getNombre(),
                        ss.getCatalogoServicio().getCategoria().name(),
                        ss.getEstado() != null ? ss.getEstado().name() : null))
                .toList();

        List<SolicitudDetalleResponse.HistorialResponse> historialResp = historialRepository
                .findBySolicitud_IdSolicitudOrderByFechaCambioDesc(s.getIdSolicitud()).stream()
                .map(h -> new SolicitudDetalleResponse.HistorialResponse(
                        h.getEstadoAnterior() != null ? h.getEstadoAnterior().name() : null,
                        h.getEstadoNuevo().name(),
                        h.getFechaCambio(),
                        h.getUsuario().getEmail(),
                        h.getObservacion()))
                .toList();

        return new SolicitudDetalleResponse(
                s.getIdSolicitud(), s.getCedulaEvaluado(), s.getNombresEvaluado(),
                s.getApellidosEvaluado(), s.getCelularEvaluado(), s.getEmailEvaluado(),
                s.getCiudadEvaluado(), s.getCargo(), s.getNotas(),
                s.getEstado() != null ? s.getEstado().name() : null,
                s.getFechaSolicitud(), s.getFechaEntregaEstimada(),
                tokenLink, itemsResp, historialResp);
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
