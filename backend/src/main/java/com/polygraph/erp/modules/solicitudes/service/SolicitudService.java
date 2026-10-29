package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.solicitudes.validacion.DatosEvaluadoValidador;
import com.polygraph.erp.modules.solicitudes.entity.SolicitudIdempotencia;
import java.math.BigDecimal;
import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.pagos.service.ClientePospagoService;
import com.polygraph.erp.modules.pagos.service.RecargoPendienteService;
import com.polygraph.erp.modules.pagos.service.SaldoServicioClienteService;
import com.polygraph.erp.shared.enums.EstadoMora;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.enums.TipoPersona;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.entity.ProcesoTipoProgreso;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.catalogo.repository.ProcesoTipoProgresoRepository;
import com.polygraph.erp.modules.pagos.entity.RecargoPendiente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.repository.CandidatoRepository;
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
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import com.polygraph.erp.shared.service.DiasHabilesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    private final com.polygraph.erp.shared.service.TiempoServicioService tiempoServicioService;
    private final com.polygraph.erp.modules.servicios.service.SemaforoTiempoService semaforoTiempoService;
    private final ProcesoTipoProgresoRepository procesoTipoProgresoRepository;
    private final CiudadRepository ciudadRepository;
    private final ServicioSubprocesoRepository servicioSubprocesoRepository;
    private final SaldoServicioClienteService saldoServicioClienteService;
    private final RecargoPendienteService recargoPendienteService;
    private final ClientePospagoService clientePospagoService;
    private final com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService calificacionRiesgoService;
    private final com.polygraph.erp.modules.servicios.repository.NovedadSubprocesoRepository novedadSubprocesoRepository;
    private final com.polygraph.erp.modules.servicios.repository.NovedadSeguimientoRepository novedadSeguimientoRepository;
    private final com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository hojaVidaEvaluadoRepository;
    private final com.polygraph.erp.modules.evaluados.repository.DocumentoEvaluadoRepository documentoEvaluadoRepository;
    private final com.polygraph.erp.modules.catalogo.repository.DocumentoRequeridoProcesoRepository documentoRequeridoProcesoRepository;
    private final com.polygraph.erp.modules.pagos.repository.MovimientoCreditoPospagoRepository movimientoCreditoPospagoRepository;
    private final com.polygraph.erp.modules.pagos.repository.OrdenCompraItemRepository ordenCompraItemRepository;
    private final com.polygraph.erp.modules.pagos.repository.LotePrepagoRepository lotePrepagoRepository;
    private final com.polygraph.erp.modules.pagos.service.DevolucionCobroService devolucionCobroService;
    private final com.polygraph.erp.modules.pagos.service.MoraService moraService;
    private final org.springframework.context.ApplicationEventPublisher eventos;
    private final SolicitudExtraService solicitudExtraService;
    private final com.polygraph.erp.modules.solicitudes.repository.SolicitudIdempotenciaRepository idempotenciaRepository;

    /** NS-12: crea TODAS las solicitudes de una carga masiva ya validada en UNA transacción: si algo falla, no queda ninguna. Cada una sigue las mismas reglas
     *  de NS-02, NS-03 y NS-06 (lote y valor de la unidad, descuento atómico, fecha de entrega) porque pasa por el mismo método. Devuelve cuántas creó. */
    public int crearLote(List<SolicitudRequest> solicitudes, String emailUsuario) {
        int creadas = 0;
        for (SolicitudRequest r : solicitudes) creadas += crearSolicitudSinLlave(r, emailUsuario).size();
        return creadas;
    }

    /** Sin llave de idempotencia (una fila suelta de pruebas o procesos internos; el formulario siempre manda la suya). */
    public List<SolicitudDetalleResponse> crearSolicitud(SolicitudRequest request, String emailUsuario) {
        return crearSolicitudSinLlave(request, emailUsuario);
    }

    /**
     * Con llave de idempotencia (NS-03): el mismo usuario con la misma llave no cobra dos veces — el segundo envío devuelve lo que creó el primero.
     * Dos envíos simultáneos con la misma llave chocan en la restricción única y el segundo recibe 409 sin haber descontado nada.
     */
    public List<SolicitudDetalleResponse> crearSolicitud(SolicitudRequest request, String emailUsuario, String claveIdempotencia) {
        // NS-07: datos mínimos del evaluado, ANTES de tocar la llave de idempotencia (un envío rechazado no la consume).
        boolean pideDireccion = request.procesosIds() != null && procesoRepository.findAllById(request.procesosIds()).stream().anyMatch(p -> Boolean.TRUE.equals(p.getRequiereDireccion()));
        var erroresEvaluado = DatosEvaluadoValidador.errores(request, pideDireccion);
        if (!erroresEvaluado.isEmpty()) throw new ApiException(DatosEvaluadoValidador.mensaje(erroresEvaluado), HttpStatus.BAD_REQUEST);
        if (request.idCiudad() != null) {
            var ciudad = ciudadRepository.findById(request.idCiudad());
            if (ciudad.isEmpty() || Boolean.TRUE.equals(ciudad.get().getPendienteConfirmacion())) {
                throw new ApiException("Elige una ciudad de la lista de ciudades confirmadas.", HttpStatus.BAD_REQUEST);
            }
        }
        SolicitudIdempotencia marca = null;
        if (claveIdempotencia != null && !claveIdempotencia.isBlank()) {
            String clave = claveIdempotencia.trim();
            if (clave.length() > 80) throw new ApiException("La llave de idempotencia es demasiado larga.", HttpStatus.BAD_REQUEST);
            var u = usuarioRepository.findByEmail(emailUsuario).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
            var previa = idempotenciaRepository.findByIdUsuarioAndClave(u.getIdUsuario(), clave);
            if (previa.isPresent()) return respuestaDeEnvioPrevio(previa.get());
            try {
                marca = idempotenciaRepository.saveAndFlush(SolicitudIdempotencia.builder().idUsuario(u.getIdUsuario()).clave(clave)
                        .creadaEn(diasHabilesService.ahora()).build());
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                throw new ApiException("Esta solicitud ya se está enviando o ya fue enviada. Revisa Mis solicitudes antes de intentar de nuevo.", HttpStatus.CONFLICT);
            }
        }
        List<SolicitudDetalleResponse> creadas = crearSolicitudSinLlave(request, emailUsuario);
        if (marca != null) {
            marca.setIdsServicios(creadas.stream().map(d -> String.valueOf(d.idServicio())).collect(java.util.stream.Collectors.joining(",")));
            idempotenciaRepository.save(marca);
        }
        return creadas;
    }

    private List<SolicitudDetalleResponse> respuestaDeEnvioPrevio(SolicitudIdempotencia previa) {
        if (previa.getIdsServicios() == null || previa.getIdsServicios().isBlank()) {
            throw new ApiException("Esta solicitud ya se está enviando. Revisa Mis solicitudes antes de intentar de nuevo.", HttpStatus.CONFLICT);
        }
        List<SolicitudDetalleResponse> r = new ArrayList<>();
        for (String id : previa.getIdsServicios().split(",")) {
            Servicio s = servicioRepository.findById(Integer.valueOf(id)).orElse(null);
            if (s != null) r.add(construirDetalle(s, null));
        }
        return r;
    }

    private List<SolicitudDetalleResponse> crearSolicitudSinLlave(SolicitudRequest request, String emailUsuario) {
        var usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        var cliente = clienteRepository.findById(usuario.getIdCliente())
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        if (cliente.getTipoCliente() == TipoCliente.POSPAGO) {
            validarMoraPospago(cliente);
        }
        validarSinMora(cliente);   // NS-05: con una factura vencida no se pide nada, ni prepago ni pospago

        // Validar catálogo y duplicados
        List<Proceso> procesos = new ArrayList<>();
        LocalDateTime ahora = diasHabilesService.ahora();   // hora de Colombia, no la del servidor
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
            } else if (cliente.getTipoCliente() == TipoCliente.POSPAGO) {
                clientePospagoService.validarCreditoDisponible(cliente, proceso, request.idCiudad());
            }

            procesos.add(proceso);
        }

        // Formato adicional de la poligrafía y temas a evaluar: se valida antes de crear (o cobrar) nada
        var extras = solicitudExtraService.validar(procesos, request.datosAdicionales(), request.temasPoligrafia());

        // Reutilizar candidato existente o crear uno nuevo
        Candidato candidato = candidatoRepository.findByCedula(request.cedula())
                .map(existente -> completarContacto(existente, request))
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
        // Recepción efectiva, plazo copiado del catálogo y compromiso (el original y el vigente nacen iguales).
        var tiempoInicial = tiempoServicioService.datosDeCreacion(ahora, maxDiasHabiles);

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
                    .criticidadCargo(DatosEvaluadoValidador.criticidad(request.criticidadCargo()))
                    .direccionVisita(Boolean.TRUE.equals(proceso.getRequiereDireccion()) && request.direccionVisita() != null ? request.direccionVisita().trim() : null)
                    .notas(request.notas())
                    .fechaRecepcionEfectiva(tiempoInicial.recepcionEfectiva())
                    .plazoDiasHabiles(tiempoInicial.plazoDiasHabiles())
                    .fechaCompromisoOriginal(tiempoInicial.compromiso())
                    .fechaEntregaEstimada(tiempoInicial.compromiso())
                    .diasPausaHabiles(0)
                    .usuarioSolicita(usuario)
                    .build();
            Servicio guardado = servicioRepository.save(servicio);
            solicitudExtraService.guardar(guardado, extras);
            // El reloj arranca en pausa SOLO si el servicio necesita la hoja de vida del candidato (Antecedentes, no): se cierra cuando la envía (EvaluadoService).
            if (!Boolean.FALSE.equals(proceso.getRequiereHojaVida())) semaforoTiempoService.abrirPausa(guardado, com.polygraph.erp.shared.enums.OrigenPausa.CANDIDATO,
                    com.polygraph.erp.modules.servicios.service.SemaforoTiempoService.MOTIVO_HOJA_DE_VIDA, usuario.getEmail(), null);
            if (cliente.getTipoCliente() == TipoCliente.PREPAGO) {
                var unidad = saldoServicioClienteService.descontar(cliente.getIdCliente(), proceso);
                // El valor queda guardado en la solicitud: una compra posterior no lo cambia.
                guardado.setIdLotePrepago(unidad.idLote());
                guardado.setValorUnitarioPrepago(unidad.valorUnitario());
                servicioRepository.save(guardado);
                // El recargo por ciudad no se cobra aquí — queda pendiente y se suma a la siguiente
                // compra en Comprar Servicios.
                recargoPendienteService.registrarSiAplica(cliente, candidato, proceso, request.idCiudad());
            } else if (cliente.getTipoCliente() == TipoCliente.POSPAGO) {
                // Pospago sí se cobra de inmediato (proceso + recargo por ciudad si aplica), pero
                // contra el cupo de crédito — se suma a la siguiente factura de corte, no a un pago inmediato.
                clientePospagoService.descontarCredito(cliente, guardado, proceso, request.idCiudad());
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
                         " fue registrada. Fecha estimada de entrega: " + tiempoInicial.compromiso())
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

    /** Un candidato que ya existía (misma cédula) conserva lo que él mismo diligenció; solo se le llenan los
     *  datos de contacto que tenía vacíos con lo que el cliente escribió ahora, para que en su portal solo los actualice. */
    private Candidato completarContacto(Candidato c, SolicitudRequest r) {
        boolean cambio = false;
        if (vacio(c.getCelular()) && !vacio(r.celular())) { c.setCelular(r.celular().trim()); cambio = true; }
        if (vacio(c.getEmailPrincipal()) && !vacio(r.email())) { c.setEmailPrincipal(r.email().trim()); cambio = true; }
        if (c.getIdCiudadResidencia() == null && r.idCiudad() != null) { c.setIdCiudadResidencia(r.idCiudad()); cambio = true; }
        return cambio ? candidatoRepository.save(c) : c;
    }

    private static boolean vacio(String v) {
        return v == null || v.isBlank();
    }

    @Transactional(readOnly = true)
    public Page<SolicitudResponse> listarSolicitudes(Integer idCliente, String estado, Pageable pageable) {
        return listarSolicitudes(idCliente, estado, null, null, pageable);
    }

    /** tiempo: filtra por estado de tiempo (vencidas, por vencer...); orden "retraso": lo más atrasado primero. */
    @Transactional(readOnly = true)
    public Page<SolicitudResponse> listarSolicitudes(Integer idCliente, String estado, java.util.List<com.polygraph.erp.shared.enums.EstadoTiempo> tiempo,
                                                     String orden, Pageable pageable) {
        var filtro = org.springframework.data.jpa.domain.Specification
                .where(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.delCliente(idCliente))
                .and(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.conSubprocesoEn(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.estadoDeFiltro(estado)))
                .and(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.conTiempo(
                        tiempo));
        Page<Servicio> pagina = servicioRepository.findAll(filtro, org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.orden(orden)));
        List<Integer> ids = pagina.getContent().stream().map(Servicio::getIdServicio).toList();
        java.util.Map<Integer, List<com.polygraph.erp.modules.servicios.entity.ServicioSubproceso>> subprocesos = ids.isEmpty() ? java.util.Map.of()
                : servicioSubprocesoRepository.findByServicio_IdServicioInOrderByServicio_IdServicioAscTipoProgreso_OrdenAsc(ids).stream()
                        .collect(java.util.stream.Collectors.groupingBy(sp -> sp.getServicio().getIdServicio()));
        return pagina.map(s -> construirResumen(s, subprocesos.getOrDefault(s.getIdServicio(), List.of())));
    }

    /** Alimenta las tarjetas de resumen por estado de "Mis Solicitudes" en el portal del cliente
     *  — mismo cálculo que GestorService.contarPorEstado, pero acotado por idCliente en vez de
     *  idGestor. */
    @Transactional(readOnly = true)
    public ConteoEstadosResponse conteoPorEstado(Integer idCliente) {
        long pendientes    = servicioRepository.countByCliente_IdClienteAndEstado(idCliente, EstadoServicio.PENDIENTE);
        long programando   = servicioRepository.countByCliente_IdClienteAndEstado(idCliente, EstadoServicio.PROGRAMANDO);
        long enEjecucion   = servicioRepository.countByCliente_IdClienteAndEstado(idCliente, EstadoServicio.EN_EJECUCION);
        long finalizados   = servicioRepository.countByCliente_IdClienteAndEstado(idCliente, EstadoServicio.FINALIZADO);
        long publicados    = servicioRepository.countByCliente_IdClienteAndEstado(idCliente, EstadoServicio.PUBLICADO);
        long cancelados    = servicioRepository.countByCliente_IdClienteAndEstado(idCliente, EstadoServicio.CANCELADO);
        long reprogramados = servicioRepository.countByCliente_IdClienteAndEstado(idCliente, EstadoServicio.REPROGRAMADO);
        long total = pendientes + programando + enEjecucion + finalizados + publicados + cancelados + reprogramados;
        long vencidas = servicioRepository.countByCliente_IdClienteAndEstadoTiempo(idCliente, com.polygraph.erp.shared.enums.EstadoTiempo.VENCIDA);
        return new ConteoEstadosResponse(pendientes, programando, enEjecucion, finalizados, publicados, cancelados, reprogramados, total, vencidas);
    }

    @Transactional(readOnly = true)
    public SolicitudDetalleResponse obtenerDetalle(Integer idServicio, UserDetails userDetails) {
        Servicio servicio = servicioRepository.findById(idServicio)
                .orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
        Usuario usuario = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        validarPropiedad(servicio, usuario);
        return construirDetalle(servicio, null, esClienteExterno(usuario));
    }

    private static boolean esClienteExterno(Usuario usuario) {
        return usuario.getRol() == Rol.ADMIN_CLIENTE || usuario.getRol() == Rol.ANALISTA_CLIENTE;
    }

    /** Informe que el usuario SÍ puede descargar: ya pasó propiedad, estado publicado y que el
     *  subproceso tenga documento. fechaPublicado distingue una publicación de otra (si la
     *  solicitud se despublica y se vuelve a publicar, el PDF en caché anterior deja de servir). */
    public record InformeAutorizado(Integer idServicio, Long idServicioSubproceso, String nombreArchivo,
                                    LocalDateTime fechaPublicado) {}

    /** Valida el "Descargar informe" del cliente; el PDF en sí lo genera la cola (INFORME_PDF),
     *  fuera de esta transacción — ver InformePdfProcesador. */
    @Transactional(readOnly = true)
    public InformeAutorizado validarInforme(Integer idServicio, Long idServicioSubproceso, UserDetails userDetails) {
        Usuario usuario = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        return validarInforme(idServicio, idServicioSubproceso, usuario);
    }

    @Transactional(readOnly = true)
    public InformeAutorizado validarInforme(Integer idServicio, Long idServicioSubproceso, Usuario usuario) {
        Servicio servicio = servicioRepository.findById(idServicio)
                .orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
        validarPropiedad(servicio, usuario);
        ServicioSubproceso sp = servicioSubprocesoRepository.findById(idServicioSubproceso)
                .filter(x -> x.getServicio() != null && idServicio.equals(x.getServicio().getIdServicio()))
                .orElseThrow(() -> new ApiException("Informe no encontrado", HttpStatus.NOT_FOUND));
        if (!informeDisponible(servicio, sp)) {
            throw new ApiException("El informe de este subproceso todavía no está disponible.", HttpStatus.CONFLICT);
        }
        String cedula = servicio.getCandidato() != null ? servicio.getCandidato().getCedula() : String.valueOf(idServicio);
        String nombre = ("Informe " + sp.getTipoProgreso().getNombreProgreso() + " " + cedula)
                .replaceAll("[^\\p{L}\\p{N} _-]", "").trim().replace(' ', '_') + ".pdf";
        return new InformeAutorizado(idServicio, idServicioSubproceso, nombre, sp.getFechaPublicado());
    }

    private static boolean informeDisponible(Servicio servicio, ServicioSubproceso sp) {
        return servicio.getEstado() == EstadoServicio.PUBLICADO
                && sp.getEstado() == EstadoSubproceso.PUBLICADO
                && Boolean.TRUE.equals(sp.getTipoProgreso().getRequiereDocumento());
    }

    /**
     * Un ADMIN_CLIENTE/ANALISTA_CLIENTE solo puede ver o cambiar el estado de solicitudes de su
     * propio cliente — sin esto, cualquiera de esos roles podía leer o cancelar solicitudes de
     * otros clientes con solo cambiar el id en la URL (GET /requests/{id} y PATCH
     * /requests/{id}/status no validaban propiedad). Un GESTOR solo opera sobre los clientes que
     * gestiona (AccesoGestor, 403 si no — antes podía ver, cancelar o publicar solicitudes de otro
     * gestor cambiando el id). ADMIN_POLYGRAPH y ANALISTA_INTERNO no tienen esta restricción.
     */
    private void validarPropiedad(Servicio servicio, Usuario usuario) {
        boolean esClienteExterno = usuario.getRol() == Rol.ADMIN_CLIENTE || usuario.getRol() == Rol.ANALISTA_CLIENTE;
        if (esClienteExterno && !usuario.getIdCliente().equals(servicio.getCliente().getIdCliente())) {
            throw new ApiException("Esta solicitud no pertenece a tu cuenta.", HttpStatus.FORBIDDEN);
        }
        com.polygraph.erp.shared.utils.AccesoGestor.validar(servicio, usuario);
    }

    /**
     * El gestor corrige la ciudad del candidato de esta solicitud (ej. el candidato en realidad
     * está en otra ciudad). Si ya había un recargo por ciudad calculado para la ciudad anterior,
     * se reconcilia: si no se había cobrado, se descarta; si ya se cobró, se genera un crédito
     * pendiente en vez de un reembolso real. Luego se evalúa el recargo de la ciudad nueva.
     */
    public SolicitudDetalleResponse corregirCiudadCandidato(Integer idServicio, Integer idCiudadNueva, UserDetails userDetails) {
        Servicio servicio = servicioRepository.findById(idServicio)
                .orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
        Usuario actor = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        validarPropiedad(servicio, actor);

        Candidato candidato = servicio.getCandidato();
        if (candidato == null) {
            throw new ApiException("Esta solicitud no tiene candidato asociado", HttpStatus.BAD_REQUEST);
        }

        Integer idCiudadAnterior = candidato.getIdCiudadResidencia();
        if (idCiudadAnterior != null && idCiudadAnterior.equals(idCiudadNueva)) {
            return construirDetalle(servicio, null);
        }

        candidato.setIdCiudadResidencia(idCiudadNueva);
        candidatoRepository.save(candidato);

        if (servicio.getCliente().getTipoCliente() == TipoCliente.PREPAGO && servicio.getProceso() != null) {
            recargoPendienteService.reconciliarPorCambioCiudad(servicio.getCliente(), candidato, servicio.getProceso(), idCiudadNueva);
        }

        log.info("Ciudad de candidato corregida: servicio={}, candidato={}, ciudad anterior={}, ciudad nueva={}",
                idServicio, candidato.getIdCandidato(), idCiudadAnterior, idCiudadNueva);

        return construirDetalle(servicio, null);
    }

    public void cambiarEstado(Integer idServicio, CambioEstadoRequest request, UserDetails userDetails) {
        Servicio servicio = servicioRepository.findById(idServicio)
                .orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));

        var usuario = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        validarPropiedad(servicio, usuario);

        EstadoServicio nuevoEstado = EstadoServicio.valueOf(request.estado().toUpperCase());
        EstadoServicio estadoAnterior = servicio.getEstado();
        validarTransicion(usuario.getRol(), estadoAnterior, nuevoEstado);
        if (esClienteExterno(usuario)) {
            // El cliente cancela solo antes de que empiece la ejecución, y siempre dice por qué.
            if (!clientePuedeCancelar(estadoAnterior)) {
                throw new ApiException("Esta solicitud ya está en ejecución; para cancelarla escríbele a tu gestor.", HttpStatus.CONFLICT);
            }
            if (request.observacion() == null || request.observacion().isBlank()) {
                throw new ApiException("Cuéntanos el motivo de la cancelación.", HttpStatus.BAD_REQUEST);
            }
        }
        if (nuevoEstado == EstadoServicio.PUBLICADO && estadoAnterior != EstadoServicio.PUBLICADO) {
            // Publicar = entregar el informe: exige la decisión del gestor sobre el riesgo (si el
            // proceso califica) y bloquea el cálculo (docs/modelo-calificacion-riesgo.md §6).
            calificacionRiesgoService.prepararPublicacion(servicio, usuario);
        }

        servicio.setEstado(nuevoEstado);
        // NS-04: cancelar devuelve lo que costó (y no se factura); sacar la solicitud de CANCELADO lo vuelve a cobrar.
        if (nuevoEstado == EstadoServicio.CANCELADO && estadoAnterior != EstadoServicio.CANCELADO) devolucionCobroService.devolver(servicio);
        else if (estadoAnterior == EstadoServicio.CANCELADO && nuevoEstado != EstadoServicio.CANCELADO) devolucionCobroService.volverACobrar(servicio);
        semaforoTiempoService.recalcular(servicio);   // al publicar congela la entrega real y el cumplimiento
        if (estadoAnterior == EstadoServicio.PUBLICADO && nuevoEstado != EstadoServicio.PUBLICADO) {
            // Se devolvió un informe publicado: el riesgo se desbloquea y se recalcula después del
            // commit (CalificacionRiesgoService.recalcular) — un error ahí no revierte el cambio.
            eventos.publishEvent(new com.polygraph.erp.modules.riesgo.service.RecalculoRiesgoSolicitado(
                    idServicio, usuario.getIdUsuario()));
        }

        historialRepository.save(HistorialEstadoServicio.builder()
                .servicio(servicio)
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(nuevoEstado)
                .usuario(usuario)
                .fechaCambio(LocalDateTime.now())
                .observacion(request.observacion())
                .build());

        notificarCambioEstadoAlCliente(servicio, nuevoEstado);

        log.info("Estado cambiado: servicio={}, {} → {}, usuario={}",
                idServicio, estadoAnterior, nuevoEstado, userDetails.getUsername());
    }

    /** El cliente nunca se enteraba de que su solicitud avanzó, se publicó o se canceló — tenía
     *  que entrar a revisar manualmente. Se notifica a TODOS los usuarios de esa empresa (no solo
     *  a quien la creó), con mensaje distinto cuando el nuevo estado es el informe final
     *  (PUBLICADO) o una cancelación. */
    private void notificarCambioEstadoAlCliente(Servicio servicio, EstadoServicio nuevoEstado) {
        if (servicio.getCliente() == null) return;
        String nombreEvaluado = servicio.getCandidato() != null
                ? (servicio.getCandidato().getNombres() + " " + servicio.getCandidato().getApellidos()).trim()
                : "tu solicitud #" + servicio.getIdServicio();

        String titulo;
        String mensaje;
        if (nuevoEstado == EstadoServicio.PUBLICADO) {
            titulo = "Informe publicado";
            mensaje = "El informe de " + nombreEvaluado + " ya está disponible para descargar.";
        } else if (nuevoEstado == EstadoServicio.CANCELADO) {
            titulo = "Solicitud cancelada";
            mensaje = "La solicitud de " + nombreEvaluado + " fue cancelada.";
        } else if (nuevoEstado == EstadoServicio.FINALIZADO) {
            titulo = "Estudio finalizado";
            mensaje = "El estudio de " + nombreEvaluado + " terminó y está en revisión final.";
        } else {
            titulo = "Cambio de estado";
            mensaje = "La solicitud de " + nombreEvaluado + " pasó a " + nuevoEstado.name() + ".";
        }

        usuarioRepository.findByIdCliente(servicio.getCliente().getIdCliente()).stream()
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .forEach(dest -> notificacionRepository.save(Notificacion.builder()
                        .usuario(dest)
                        .tipo("SOLICITUD_ESTADO")
                        .titulo(titulo)
                        .mensaje(mensaje)
                        .leida(false)
                        .fechaCreacion(LocalDateTime.now())
                        .referenciaTipo("SERVICIO")
                        .referenciaId(servicio.getIdServicio().longValue())
                        .build()));
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

    private void validarTransicion(Rol rol, EstadoServicio actual, EstadoServicio nuevo) {
        boolean permitido = switch (rol) {
            case ADMIN_POLYGRAPH -> true;
            // El gestor ya no elige el estado del servicio a mano (SE-02): solo publica el estudio cuando todo está publicado
            // (FINALIZADO) o cancela la solicitud (con motivo). El resto se mueve con las acciones de cada subproceso.
            case GESTOR -> actual != EstadoServicio.CANCELADO &&
                           ((nuevo == EstadoServicio.PUBLICADO && actual == EstadoServicio.FINALIZADO) ||
                            nuevo == EstadoServicio.CANCELADO);
            case ADMIN_CLIENTE, ANALISTA_CLIENTE -> nuevo == EstadoServicio.CANCELADO;
            default -> false;
        };
        if (!permitido) {
            throw new ApiException("Ese cambio de estado no está permitido: usa las acciones de cada subproceso "
                    + "(asignar, aprobar, devolver, cancelar).", HttpStatus.FORBIDDEN);
        }
    }

    private SolicitudResponse construirResumen(Servicio s, List<com.polygraph.erp.modules.servicios.entity.ServicioSubproceso> sps) {
        var avance = com.polygraph.erp.shared.estados.SemaforoEstados.avance(sps.stream().map(com.polygraph.erp.modules.servicios.entity.ServicioSubproceso::getEstado).toList());
        return new SolicitudResponse(
                s.getIdServicio(),
                s.getCandidato() != null ? s.getCandidato().getCedula()     : null,
                s.getCandidato() != null ? s.getCandidato().getNombres()    : null,
                s.getCandidato() != null ? s.getCandidato().getApellidos() : null,
                s.getCargo(),
                s.getEstado() != null ? s.getEstado().name() : null,
                s.getFechaSolicitud(), s.fechaParaMostrar(),
                s.getProceso() != null ? s.getProceso().getNombreProceso() : null,
                tiempoServicioService.de(s),
                sps.stream().map(sp -> new SolicitudResponse.SubprocesoResumen(sp.getTipoProgreso().getNombreProgreso(), sp.getEstado().name())).toList(),
                avance.publicados(), avance.vigentes(),
                com.polygraph.erp.shared.estados.SemaforoEstados.etiqueta(avance).texto(),
                com.polygraph.erp.modules.servicios.dto.ServicioSemaforoFila.ResumenEstado.de(sps.stream().map(com.polygraph.erp.modules.servicios.entity.ServicioSubproceso::getEstado).toList()));
    }

    private SolicitudDetalleResponse construirDetalle(Servicio s, String tokenLink) {
        return construirDetalle(s, tokenLink, false);
    }

    /** paraCliente: el cliente no ve el correo del empleado interno que hizo cada cambio. */
    private SolicitudDetalleResponse construirDetalle(Servicio s, String tokenLink, boolean paraCliente) {
        List<SolicitudDetalleResponse.HistorialResponse> historialResp = historialRepository
                .findByServicio_IdServicioOrderByFechaCambioDesc(s.getIdServicio()).stream()
                .map(h -> new SolicitudDetalleResponse.HistorialResponse(
                        h.getEstadoAnterior() != null ? h.getEstadoAnterior().name() : null,
                        h.getEstadoNuevo().name(),
                        h.getFechaCambio(),
                        paraCliente || h.getUsuario() == null ? null : h.getUsuario().getEmail(),
                        h.getObservacion()))
                .toList();

        Cliente cliente = s.getCliente();
        Candidato candidato = s.getCandidato();

        Ciudad ciudad = candidato != null && candidato.getIdCiudadResidencia() != null
                ? ciudadRepository.findById(candidato.getIdCiudadResidencia()).orElse(null)
                : null;

        RecargoPendiente recargo = candidato != null && s.getProceso() != null
                ? recargoPendienteService.buscarPara(candidato.getIdCandidato(), s.getProceso().getIdProceso())
                : null;

        List<RecargoPendiente> historialRecargo = candidato != null && s.getProceso() != null
                ? recargoPendienteService.historial(candidato.getIdCandidato(), s.getProceso().getIdProceso())
                : List.of();
        List<SolicitudDetalleResponse.RecargoCiudadMovimientoResponse> historialRecargoResp = historialRecargo.stream()
                .map(r -> new SolicitudDetalleResponse.RecargoCiudadMovimientoResponse(
                        r.getCiudad() != null ? r.getCiudad().getNombreCiudad() : null,
                        r.getMonto(), r.getMotivo(), r.getEstado().name(), r.getFechaGenerado(), r.getFechaPagado()))
                .toList();
        BigDecimal totalPagadoRecargo = historialRecargo.stream()
                .filter(r -> r.getEstado() == com.polygraph.erp.shared.enums.EstadoRecargoPendiente.PAGADO)
                .map(RecargoPendiente::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPendienteRecargo = historialRecargo.stream()
                .filter(r -> r.getEstado() == com.polygraph.erp.shared.enums.EstadoRecargoPendiente.PENDIENTE)
                .map(RecargoPendiente::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ServicioSubproceso> subprocesos = servicioSubprocesoRepository
                .findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(s.getIdServicio());
        List<SolicitudDetalleResponse.SubprocesoResponse> subprocesosResp = subprocesos.stream()
                .map(sp -> new SolicitudDetalleResponse.SubprocesoResponse(
                        sp.getId(),
                        sp.getTipoProgreso().getNombreProgreso(),
                        sp.getEstado().name(),
                        sp.getFechaProgramada(),
                        informeDisponible(s, sp)))
                .toList();

        SolicitudDetalleResponse.ResultadoRiesgoResponse riesgo = s.getEstado() == EstadoServicio.PUBLICADO
                ? calificacionRiesgoService.resultadoParaCliente(s.getIdServicio())
                        .map(r -> new SolicitudDetalleResponse.ResultadoRiesgoResponse(r.getKey(), r.getValue()))
                        .orElse(null)
                : null;

        return new SolicitudDetalleResponse(
                s.getIdServicio(),
                candidato != null ? candidato.getCedula()      : null,
                candidato != null ? candidato.getNombres()     : null,
                candidato != null ? candidato.getApellidos()  : null,
                candidato != null ? candidato.getCelular()    : null,
                candidato != null ? candidato.getEmailPrincipal() : null,
                s.getCargo(), s.getNotas(),
                s.getProceso() != null ? s.getProceso().getIdProceso() : null,
                s.getProceso() != null ? s.getProceso().getNombreProceso() : null,
                s.getProceso() != null && s.getProceso().getClasificacion() != null
                        ? s.getProceso().getClasificacion().getNombre() : null,
                s.getEstado() != null ? s.getEstado().name() : null,
                s.getFechaSolicitud(), s.getHoraSolicitud(), s.fechaParaMostrar(),
                tokenLink, historialResp,
                cliente != null ? nombreCliente(cliente)      : null,
                cliente != null ? cliente.getNit()            : null,
                cliente != null ? cliente.getTelefono()       : null,
                cliente != null ? cliente.getEmailPrincipal() : null,
                ciudad != null ? ciudad.getIdCiudad() : null,
                ciudad != null ? ciudad.getNombreCiudad() : null,
                recargo != null ? recargo.getMonto() : null,
                recargo != null ? recargo.getEstado().name() : null,
                totalPagadoRecargo,
                totalPendienteRecargo,
                historialRecargoResp,
                subprocesosResp,
                riesgo,
                novedadesParaCliente(subprocesos),
                estadoCandidato(s),
                documentosCandidato(s),
                clientePuedeCancelar(s.getEstado()),
                cobro(s, totalPagadoRecargo, totalPendienteRecargo),
                tiempoServicioService.de(s));
    }

    /** Cuánto le vale la solicitud al cliente — ver SolicitudDetalleResponse.CobroResponse. */
    /** NS-04: solicitud cancelada cuyo cobro ya se devolvió — el cliente lo ve como «Cancelada · no se factura». */
    public static final String ESTADO_COBRO_CANCELADA = "CANCELADA";

    private SolicitudDetalleResponse.CobroResponse cobro(Servicio s, BigDecimal recargoPagado, BigDecimal recargoPendiente) {
        Cliente cliente = s.getCliente();
        if (cliente == null) return null;
        if (cliente.getTipoCliente() == TipoCliente.POSPAGO) {
            return movimientoCreditoPospagoRepository.findFirstByServicio_IdServicioOrderByFechaGeneradoDesc(s.getIdServicio())
                    .map(m -> {
                        var f = m.getFactura();
                        return new SolicitudDetalleResponse.CobroResponse("POSPAGO", m.getMontoBase(), null, null,
                                m.getMontoRecargoCiudad(), BigDecimal.ZERO, m.getAnuladoEn() != null ? BigDecimal.ZERO : m.getMontoTotal(), false,
                                f != null ? f.getReferencia() : null,
                                m.getAnuladoEn() != null ? ESTADO_COBRO_CANCELADA : f != null ? f.getEstado().name() : "SIN_FACTURAR",
                                f != null ? f.getFechaVencimiento() : null,
                                f != null ? f.getFechaPago() : null);
                    })
                    .orElse(null);
        }
        if (s.getProceso() == null) return null;
        // Con lote (NS-02): la compra de donde salió la unidad, no la última compra. Sin lote (solicitud anterior sin respaldo): la última aprobada, como antes.
        var item = s.getIdLotePrepago() != null
                ? lotePrepagoRepository.findById(s.getIdLotePrepago()).map(com.polygraph.erp.modules.pagos.entity.LotePrepago::getItem).orElse(null)
                : ordenCompraItemRepository
                .findFirstByOrdenCompra_Cliente_IdClienteAndProceso_IdProcesoAndOrdenCompra_EstadoOrderByOrdenCompra_FechaCreacionDesc(
                        cliente.getIdCliente(), s.getProceso().getIdProceso(), com.polygraph.erp.shared.enums.EstadoOrdenCompra.APROBADA)
                .orElse(null);
        BigDecimal valor = s.getValorUnitarioPrepago() != null ? s.getValorUnitarioPrepago() : item != null ? item.getValorUnitario() : null;
        BigDecimal pagado = recargoPagado != null ? recargoPagado : BigDecimal.ZERO;
        BigDecimal pendiente = recargoPendiente != null ? recargoPendiente : BigDecimal.ZERO;
        if (valor == null && pagado.signum() == 0 && pendiente.signum() == 0) return null;
        BigDecimal sinDescuento = item != null && item.getValorOriginal() != null
                && item.getValorOriginal().compareTo(item.getValorUnitario()) != 0 ? item.getValorOriginal() : null;
        return new SolicitudDetalleResponse.CobroResponse("PREPAGO", valor, sinDescuento,
                item != null ? item.getNombreDescuentoAplicado() : null, pagado, pendiente,
                s.getCobroDevueltoEn() != null ? BigDecimal.ZERO : (valor != null ? valor : BigDecimal.ZERO).add(pagado).add(pendiente), true,
                item != null ? item.getOrdenCompra().getReferencia() : null,
                s.getCobroDevueltoEn() != null ? ESTADO_COBRO_CANCELADA : item != null ? item.getOrdenCompra().getEstado().name() : null,
                null, item != null ? item.getOrdenCompra().getFechaActualizacion() : null);
    }

    /** El cliente cancela solo mientras no ha empezado la ejecución del estudio. */
    private static boolean clientePuedeCancelar(EstadoServicio estado) {
        return estado == EstadoServicio.PENDIENTE || estado == EstadoServicio.PROGRAMANDO;
    }

    /** Documentos que pide el proceso (y los que el candidato subió por su cuenta), marcando cuáles
     *  ya cargó. Solo nombre y fecha: el cliente no abre los documentos del candidato. */
    private List<SolicitudDetalleResponse.DocumentoCandidatoResponse> documentosCandidato(Servicio s) {
        Candidato candidato = s.getCandidato();
        if (candidato == null) return List.of();
        List<com.polygraph.erp.modules.evaluados.entity.DocumentoEvaluado> cargados = hojaVidaEvaluadoRepository
                .findByCandidato_IdCandidato(candidato.getIdCandidato())
                .map(hv -> documentoEvaluadoRepository.findByHojaVidaEvaluado_IdOrderByFechaCargaDesc(hv.getId()))
                .orElse(List.of());
        java.util.Map<String, com.polygraph.erp.modules.evaluados.entity.DocumentoEvaluado> porTipo = new java.util.HashMap<>();
        for (var d : cargados) porTipo.putIfAbsent(d.getTipoDocumento().toLowerCase(), d); // vale el más reciente

        List<SolicitudDetalleResponse.DocumentoCandidatoResponse> resultado = new ArrayList<>();
        java.util.Set<String> requeridos = new java.util.HashSet<>();
        if (s.getProceso() != null) {
            for (var r : documentoRequeridoProcesoRepository.findByProceso_IdProcesoOrderByOrdenAscNombreAsc(s.getProceso().getIdProceso())) {
                String clave = r.getClave().toLowerCase();
                requeridos.add(clave);
                var d = porTipo.get(clave);
                resultado.add(new SolicitudDetalleResponse.DocumentoCandidatoResponse(r.getNombre(), true,
                        Boolean.TRUE.equals(r.getObligatorio()), d != null, d != null ? d.getFechaCarga() : null,
                        estadoRevision(d), d != null ? d.getObservacionRevision() : null));
            }
        }
        porTipo.forEach((clave, d) -> {
            if (requeridos.contains(clave)) return;
            String nombre = clave.replace('_', ' ');
            resultado.add(new SolicitudDetalleResponse.DocumentoCandidatoResponse(
                    Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1), false, false, true, d.getFechaCarga(),
                    estadoRevision(d), d.getObservacionRevision()));
        });
        return resultado;
    }

    private static String estadoRevision(com.polygraph.erp.modules.evaluados.entity.DocumentoEvaluado d) {
        if (d == null) return null;
        return d.getEstadoRevision() != null ? d.getEstadoRevision().name() : "PENDIENTE";
    }

    /** Novedades de tipos marcados "visible para el cliente" (V193), la más reciente primero. */
    private List<SolicitudDetalleResponse.NovedadClienteResponse> novedadesParaCliente(List<ServicioSubproceso> subprocesos) {
        List<com.polygraph.erp.modules.servicios.entity.NovedadSubproceso> visibles = subprocesos.stream()
                .flatMap(sp -> novedadSubprocesoRepository.findByServicioSubproceso_IdOrderByFechaRegistroAsc(sp.getId()).stream())
                .filter(n -> n.getTipo() != null && Boolean.TRUE.equals(n.getTipo().getVisibleCliente()))
                .sorted(java.util.Comparator.comparing(com.polygraph.erp.modules.servicios.entity.NovedadSubproceso::getFechaRegistro).reversed())
                .toList();
        if (visibles.isEmpty()) return List.of();
        // Nueva fecha si al resolverla se reprogramó (la última REPROGRAMACION de su seguimiento).
        java.util.Map<Long, LocalDateTime> reprogramaciones = new java.util.HashMap<>();
        novedadSeguimientoRepository.findByNovedad_IdInOrderByFechaAscIdAsc(visibles.stream().map(n -> n.getId()).toList())
                .stream()
                .filter(sg -> sg.getTipo() == com.polygraph.erp.shared.enums.TipoSeguimientoNovedad.REPROGRAMACION && sg.getFechaNueva() != null)
                .forEach(sg -> reprogramaciones.put(sg.getNovedad().getId(), sg.getFechaNueva()));
        return visibles.stream().map(n -> new SolicitudDetalleResponse.NovedadClienteResponse(
                n.getId(),
                n.getServicioSubproceso().getTipoProgreso().getNombreProgreso(),
                n.getTipo().getNombre(),
                n.getTipo().getCategoria() != null ? n.getTipo().getCategoria().getNombre() : null,
                n.getTipo().getCategoria() != null ? n.getTipo().getCategoria().getColor() : null,
                n.getEstado().name(),
                n.getTipo().getClave() != null && n.getTipo().getClave().startsWith("DOCUMENTO_") ? n.getDescripcion() : null,
                n.getFechaRegistro(),
                n.getRespuesta(),
                n.getFechaResolucion(),
                reprogramaciones.get(n.getId()))).toList();
    }

    /** ¿Ya diligenció el candidato su hoja de vida? — para que el cliente sepa si el estudio está
     *  detenido esperando al candidato. */
    private SolicitudDetalleResponse.EstadoCandidatoResponse estadoCandidato(Servicio s) {
        Candidato candidato = s.getCandidato();
        if (candidato == null) return null;
        var hojaVida = hojaVidaEvaluadoRepository.findByCandidato_IdCandidato(candidato.getIdCandidato()).orElse(null);
        Integer porcentaje = hojaVida != null ? hojaVida.getProgresoPorcentaje() : null;
        Integer paso = hojaVida != null ? hojaVida.getPasoActual() : null;
        if (hojaVida != null && Boolean.TRUE.equals(hojaVida.getCompletado())) {
            return new SolicitudDetalleResponse.EstadoCandidatoResponse("COMPLETADO", null, 100, paso);
        }
        return linkCandidatoRepository.findTopByServicio_IdServicioOrderByFechaCreacionDesc(s.getIdServicio())
                // Desde V197 el link no vence por tiempo: "VENCIDO" = el candidato ya no puede entrar
                // (revocado, bloqueado o servicio cerrado) — ver EstadoLink.
                .map(l -> new SolicitudDetalleResponse.EstadoCandidatoResponse(
                        com.polygraph.erp.modules.servicios.entity.EstadoLink.de(l).conAcceso() ? "PENDIENTE" : "VENCIDO",
                        null, porcentaje, paso))
                .orElse(new SolicitudDetalleResponse.EstadoCandidatoResponse("SIN_LINK", null, porcentaje, paso));
    }

    /** Genera una fila de asignación (PENDIENTE) por cada subproceso habilitado del proceso del servicio. */
    /** Regla de negocio: "Cliente con mora no puede solicitar servicios" — solo aplica a pospago (prepago se limita por saldo). */
    private void validarMoraPospago(Cliente cliente) {
        ClientePospago pospago = clientePospagoRepository.findByIdCliente(cliente.getIdCliente()).orElse(null);
        if (pospago == null) return; // sin fila de pospago: ClientePospagoService.validarCreditoDisponible ya lo bloquea más abajo.

        // Regla de negocio: "Cliente pospago requiere estudio de crédito previo" — admin marca
        // requiereAprobacion=true mientras el estudio de crédito sigue pendiente; hasta que lo
        // apruebe (lo pone en false desde ClienteAdminService.actualizarPospago), el cliente no
        // puede pedir servicios aunque ya tenga un límite de crédito asignado.
        if (Boolean.TRUE.equals(pospago.getRequiereAprobacion())) {
            throw new ApiException(
                    "No puedes solicitar servicios todavía: tu cuenta pospago está pendiente de aprobación "
                    + "(estudio de crédito). Contacta a tu gestor.",
                    HttpStatus.FORBIDDEN);
        }

        // La mora SALE de las facturas (validarSinMora). Aquí solo queda la suspensión que decide el administrador.
        if (pospago.getEstadoMora() == EstadoMora.SUSPENDIDO) {
            throw new ApiException("No puedes solicitar servicios: tu cuenta está suspendida. Contacta a tu gestor para regularizar tu situación.", HttpStatus.FORBIDDEN);
        }
    }

    /** El único rechazo por mora: calculada desde las facturas y con el mismo texto que ve el cliente en pantalla (`moraDelUsuario`). */
    private void validarSinMora(Cliente cliente) {
        var mora = moraService.calcular(cliente.getIdCliente());
        if (mora.enMora()) throw new ApiException(mora.mensaje(), HttpStatus.FORBIDDEN);
    }

    /** Para que la pantalla de Nueva solicitud avise ANTES de enviar; devuelve el mismo mensaje con el que el servidor rechaza. */
    @Transactional(readOnly = true)
    public com.polygraph.erp.modules.pagos.mora.MoraCliente.Resultado moraDelUsuario(String emailUsuario) {
        var usuario = usuarioRepository.findByEmail(emailUsuario).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        return usuario.getIdCliente() == null ? com.polygraph.erp.modules.pagos.mora.MoraCliente.Resultado.SIN_MORA : moraService.calcular(usuario.getIdCliente());
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
                    .estado(EstadoSubproceso.PENDIENTE)
                    .fechaCreacion(ahora)
                    // Estampa la versión de plantilla vigente en este momento — ver
                    // TipoProgreso.versionPlantilla y FormularioPlantillaService.guardar().
                    .versionPlantilla(paso.getTipoProgreso().getVersionPlantilla())
                    .build();
            servicioSubprocesoRepository.save(subproceso);

            // Poligrafía y visita domiciliaria no se asignan solas ni las programa el gestor: el
            // PROGRAMADOR recibe la notificación y hace la asignación manual (elige poligrafista/
            // visitador según la ciudad del servicio) desde su propio módulo de asignaciones.
            Rol rolResponsable = paso.getTipoProgreso().getRolResponsable();
            if (rolResponsable == Rol.POLIGRAFISTA || rolResponsable == Rol.VISITADOR) {
                notificarProgramadorParaAsignar(servicio, paso.getTipoProgreso().getNombreProgreso(), ahora);
            }
        }
    }

    private void notificarProgramadorParaAsignar(Servicio servicio, String nombreSubproceso, LocalDateTime ahora) {
        var candidato = servicio.getCandidato();
        String nombreEvaluado = candidato != null
                ? (candidato.getNombres() + " " + (candidato.getApellidos() != null ? candidato.getApellidos() : "")).trim()
                : "el evaluado";
        Integer idServicio = servicio.getIdServicio();

        usuarioRepository.findAll().stream()
                .filter(u -> Rol.PROGRAMADOR.equals(u.getRol()) && Boolean.TRUE.equals(u.getActivo()))
                .forEach(programador -> notificacionRepository.save(Notificacion.builder()
                        .usuario(programador)
                        .tipo("PROGRAMAR_ASIGNACION")
                        .titulo(nombreSubproceso + " por programar")
                        .mensaje("El servicio de " + nombreEvaluado + " requiere programar " + nombreSubproceso + ".")
                        .leida(false)
                        .fechaCreacion(ahora)
                        .referenciaTipo("SERVICIO")
                        .referenciaId(idServicio.longValue())
                        .build()));
    }

    private String nombreCliente(Cliente c) {
        if (TipoPersona.JURIDICA.equals(c.getTipoPersona()) && c.getRazonSocial() != null) {
            return c.getRazonSocial();
        }
        String nombre = c.getNombre() != null ? c.getNombre() : "";
        String apellido = c.getApellido() != null ? " " + c.getApellido() : "";
        return (nombre + apellido).trim();
    }
}
