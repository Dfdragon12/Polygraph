package com.polygraph.erp.modules.evaluados.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.dto.DocumentoRequeridoResponse;
import com.polygraph.erp.modules.catalogo.entity.ItemHojaVida;
import com.polygraph.erp.modules.catalogo.entity.ListaHojaVida;
import com.polygraph.erp.modules.catalogo.repository.DocumentoRequeridoProcesoRepository;
import com.polygraph.erp.modules.catalogo.repository.ItemHojaVidaRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.dto.*;
import com.polygraph.erp.modules.evaluados.entity.*;
import com.polygraph.erp.modules.evaluados.repository.CandidatoRepository;
import com.polygraph.erp.modules.evaluados.repository.DocumentoEvaluadoRepository;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.pagos.service.RecargoPendienteService;
import com.polygraph.erp.modules.servicios.entity.EstadoLink;
import com.polygraph.erp.modules.servicios.entity.HistorialEstadoServicio;
import com.polygraph.erp.shared.enums.EstadoRevisionDocumento;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.HistorialEstadoServicioRepository;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.shared.entity.Notificacion;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import com.polygraph.erp.shared.service.DiasHabilesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EvaluadoService {

    private final LinkCandidatoRepository linkCandidatoRepository;
    private final HojaVidaEvaluadoRepository hojaVidaRepository;
    private final CandidatoRepository candidatoRepository;
    private final HistorialLaboralService historialLaboralService;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionRepository notificacionRepository;
    private final DocumentoEvaluadoRepository documentoRepository;
    private final DocumentoRequeridoProcesoRepository documentoRequeridoProcesoRepository;
    private final AlmacenamientoDocumentosService almacenamientoService;
    private final RecargoPendienteService recargoPendienteService;
    private final HistorialEstadoServicioRepository historialEstadoServicioRepository;
    private final com.polygraph.erp.modules.servicios.service.SemaforoTiempoService semaforoTiempoService;
    private final ValidadorArchivoCandidato validadorArchivo;
    private final ItemHojaVidaRepository itemHojaVidaRepository;
    private final com.polygraph.erp.modules.auth.service.AceptacionLegalService aceptacionLegalService;

    /** Niveles educativos del portal, del más bajo al más alto — con esto se saca el "nivel
     *  educativo" del candidato de sus estudios (ya no lo escribe aparte). */
    private static final List<String> NIVELES_EDUCATIVOS = List.of(
            "Bachiller básico (hasta noveno grado)", "Bachiller académico (hasta grado once)",
            "Técnico laboral (SENA u otras instituciones)", "Técnico", "Técnico profesional", "Tecnológico", "Profesional",
            "Especialización", "Maestría", "Doctorado", "Posdoctorado");
    private static final String SIN_ESTUDIOS = "Sin estudios";
    private static final String ESTADO_EN_CURSO = "En curso";
    private static final List<String> ESTADOS_ESTUDIO = List.of(
            "Graduado", "Terminó materias, pendiente de grado", ESTADO_EN_CURSO, "Retirado sin graduarse", "Aplazado");

    @Transactional
    public LinkValidacionResponse validarLink(String token) {
        LinkCandidato link = obtenerLinkValido(token);
        Servicio servicio = obtenerServicio(link);
        Candidato candidato = servicio.getCandidato();

        if (link.getFechaPrimerIngreso() == null) {
            link.setFechaPrimerIngreso(LocalDateTime.now());
            linkCandidatoRepository.save(link);
        }

        HojaVidaEvaluado hv = candidato != null
                ? hojaVidaRepository.findByCandidato_IdCandidato(candidato.getIdCandidato()).orElse(null)
                : null;
        boolean completada = hv != null && Boolean.TRUE.equals(hv.getCompletado());
        boolean autorizo = hv != null && Boolean.TRUE.equals(hv.getAutorizacionDatos());

        List<DocumentoRequeridoResponse> documentosRequeridos = servicio.getProceso() != null
                ? documentoRequeridoProcesoRepository
                        .findByProceso_IdProcesoOrderByOrdenAscNombreAsc(servicio.getProceso().getIdProceso())
                        .stream().map(DocumentoRequeridoResponse::from).toList()
                : List.of();

        return new LinkValidacionResponse(
                servicio.getIdServicio(),
                candidato != null ? candidato.getCedula()     : null,
                candidato != null ? candidato.getNombres()    : null,
                candidato != null ? candidato.getApellidos() : null,
                servicio.getCargo(),
                servicio.getProceso() != null ? servicio.getProceso().getNombreProceso() : null,
                nombreEmpresa(servicio.getCliente()),
                completada,
                autorizo,
                autorizo ? hv.getFechaAutorizacion() : null,
                validadorArchivo.maxPdfMb(),
                validadorArchivo.maxImagenMb(),
                documentosRequeridos);
    }

    /** Nombre de la empresa cliente como lo ve el candidato: comercial, si no razón social, si no nombre. */
    private static String nombreEmpresa(com.polygraph.erp.modules.clientes.entity.Cliente c) {
        if (c == null) return null;
        if (c.getNombreComercial() != null && !c.getNombreComercial().isBlank()) return c.getNombreComercial();
        if (c.getRazonSocial() != null && !c.getRazonSocial().isBlank()) return c.getRazonSocial();
        return c.getNombre();
    }

    @Transactional(readOnly = true)
    public HojaVidaRequest obtenerFormulario(String token) {
        LinkCandidato link = obtenerLinkValido(token);
        Servicio servicio = obtenerServicio(link);
        Candidato candidato = servicio.getCandidato();

        Long idCandidato = candidato != null ? candidato.getIdCandidato() : null;

        Integer idCiudadNacimiento = candidato != null ? candidato.getIdCiudadNacimiento() : null;
        Integer idCiudadResidencia = candidato != null ? candidato.getIdCiudadResidencia() : null;
        Integer idPaisNacimiento = candidato != null ? candidato.getIdPaisNacimiento() : null;
        Integer idPaisResidencia = candidato != null ? candidato.getIdPaisResidencia() : null;
        LocalDate fechaExpedicion = candidato != null ? candidato.getFechaExpedicion() : null;
        Integer idCiudadExpedicion = candidato != null ? candidato.getIdCiudadExpedicion() : null;

        return (idCandidato != null ? hojaVidaRepository.findByCandidato_IdCandidato(idCandidato) : java.util.Optional.<HojaVidaEvaluado>empty())
                .map(hv -> new HojaVidaRequest(
                        hv.getAutorizacionDatos(),
                        hv.getFechaNacimiento(),
                        hv.getLugarNacimiento(),
                        idCiudadNacimiento,
                        idCiudadResidencia,
                        hv.getEstadoCivil(),
                        hv.getNivelEducativo(),
                        hv.getDireccion(),
                        hv.getBarrio(),
                        hv.getEstrato(),
                        // Si el candidato aún no ha escrito su correo/celular, va lo que puso el cliente al solicitar
                        vacioANulo(hv.getEmail()) != null ? hv.getEmail() : candidato != null ? candidato.getEmailPrincipal() : null,
                        vacioANulo(hv.getCelular()) != null ? hv.getCelular() : candidato != null ? candidato.getCelular() : null,
                        hv.getRh(),
                        hv.getLibretaMilitar(),
                        hv.getVisa(),
                        hv.getPasaporte(),
                        hv.getFondoPensiones(),
                        hv.getEps(),
                        hv.getTelefonoFijo(),
                        hv.getEducacion().stream()
                                .map(e -> new EducacionRequest(e.getNivel(), e.getInstitucion(),
                                        e.getTitulo(), e.getFechaInicio(), e.getFechaFin(),
                                        e.getEnCurso(), e.getCiudad(),
                                        e.getEstado(), e.getEnExterior(), e.getPaisExterior()))
                                .toList(),
                        hv.getExperienciaLaboral().stream()
                                .map(ex -> new ExperienciaLaboralRequest(ex.getEmpresa(), ex.getCargo(),
                                        ex.getFechaInicio(), ex.getFechaFin(), ex.getLaboraActualmente(),
                                        ex.getCiudad(), ex.getTelefonoEmpresa(), ex.getMotivoRetiro(),
                                        ex.getNombreJefe(), ex.getCargoJefe(),
                                        ex.getTipoContrato(), ex.getSalario(), ex.getLlamadosAtencion(),
                                        ex.getContratadoPorTemporal(), ex.getNombreTemporal()))
                                .toList(),
                        hv.getReferencias().stream()
                                .map(r -> new ReferenciaPersonalRequest(r.getNombre(), r.getParentesco(),
                                        r.getTelefono(), r.getTiempoConocimiento(),
                                        r.getConfirmaNoFamiliar(), r.getHabloUltimos30Dias()))
                                .toList(),
                        hv.getInactividades().stream()
                                .filter(i -> i.getJustificacion() != null)
                                .collect(java.util.stream.Collectors.toMap(
                                        i -> HistorialLaboralService.clave(i, hv.getExperienciaLaboral()),
                                        InactividadLaboralEvaluado::getJustificacion,
                                        (a, b) -> a)),
                        hv.getPasoActual(),
                        idPaisNacimiento, idPaisResidencia, hv.getIdBarrio(),
                        fechaExpedicion, idCiudadExpedicion,
                        hv.getDireccionAdicional(),
                        hv.getTieneVisa(), hv.getVisaPais(), hv.getTienePasaporte(),
                        Boolean.TRUE.equals(hv.getSinEstudios()),
                        hv.getRespuestas().stream()
                                .filter(r -> r.getIdItem() != null)
                                .map(r -> new RespuestaHojaVidaRequest(r.getIdItem(), r.getRespuesta(), r.getDetalle()))
                                .toList(),
                        hv.getInactividades().stream()
                                .filter(i -> i.getActividad() != null || i.getQuienConfirma() != null)
                                .collect(java.util.stream.Collectors.toMap(
                                        i -> HistorialLaboralService.clave(i, hv.getExperienciaLaboral()),
                                        i -> new HojaVidaRequest.DetalleInactividad(i.getActividad(), i.getQuienConfirma()),
                                        (a, b) -> a))))
                .orElse(new HojaVidaRequest(false, null, null,
                        idCiudadNacimiento, idCiudadResidencia,
                        null, null, null, null, null,
                        candidato != null ? candidato.getEmailPrincipal() : null,
                        candidato != null ? candidato.getCelular() : null,
                        null, null, null, null, null, null, null,
                        List.of(), List.of(), List.of(), Map.of(), null,
                        idPaisNacimiento, idPaisResidencia, null,
                        fechaExpedicion, idCiudadExpedicion,
                        null, null, null, null, false, List.of()));
    }

    private static String vacioANulo(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    /** El candidato acepta la autorización de tratamiento de datos (Ley 1581 de 2012). Es lo
     *  primero que ve al abrir el link: sin esto no puede diligenciar, cargar documentos ni
     *  proponer horarios. Queda registrada la fecha de la primera aceptación. */
    public void autorizarTratamientoDatos(String token, String ip) {
        Servicio servicio = obtenerServicio(obtenerLinkValido(token));
        Candidato candidato = servicio.getCandidato();
        if (candidato == null) {
            throw new ApiException("El servicio no tiene un candidato asociado", HttpStatus.CONFLICT);
        }
        HojaVidaEvaluado hojaVida = obtenerOCrearHojaVida(candidato);
        if (Boolean.TRUE.equals(hojaVida.getAutorizacionDatos())) return;
        hojaVida.setAutorizacionDatos(true);
        if (hojaVida.getFechaAutorizacion() == null) hojaVida.setFechaAutorizacion(LocalDateTime.now());
        hojaVidaRepository.save(hojaVida);
        aceptacionLegalService.registrarAutorizacionCandidato(candidato.getIdCandidato(), servicio.getIdServicio(), ip);
        log.info("Candidato {} aceptó la autorización de tratamiento de datos (servicio {})",
                candidato.getIdCandidato(), servicio.getIdServicio());
    }

    /** 403 si el candidato de este servicio todavía no aceptó la autorización de datos. */
    public void exigirAutorizacion(Servicio servicio) {
        Candidato candidato = servicio.getCandidato();
        boolean autorizo = candidato != null && hojaVidaRepository.findByCandidato_IdCandidato(candidato.getIdCandidato())
                .map(hv -> Boolean.TRUE.equals(hv.getAutorizacionDatos()))
                .orElse(false);
        if (!autorizo) {
            throw new ApiException("Primero debes aceptar la autorización de tratamiento de datos.", HttpStatus.FORBIDDEN);
        }
    }

    /** Documentos y citas se habilitan cuando el candidato ENVÍA su hoja de vida (el portal solo
     *  muestra Inicio y Hoja de vida antes). 403 si todavía no la ha enviado. */
    public void exigirHojaVidaEnviada(Servicio servicio) {
        Candidato candidato = servicio.getCandidato();
        boolean enviada = candidato != null && hojaVidaRepository.findByCandidato_IdCandidato(candidato.getIdCandidato())
                .map(hv -> Boolean.TRUE.equals(hv.getCompletado()))
                .orElse(false);
        if (!enviada) {
            throw new ApiException("Primero completa y envía tu hoja de vida.", HttpStatus.FORBIDDEN);
        }
    }

    public void guardarProgreso(String token, HojaVidaRequest request) {
        LinkCandidato link = obtenerLinkValido(token);
        Servicio servicio = obtenerServicio(link);
        guardar(servicio, request, false);
    }

    public void enviarFormulario(String token, HojaVidaRequest request) {
        LinkCandidato link = obtenerLinkValido(token);

        if (!Boolean.TRUE.equals(request.autorizacionDatos())) {
            throw new ApiException("Debe aceptar la autorización de manejo de datos", HttpStatus.BAD_REQUEST);
        }

        validarDeclaraciones(request);
        validarRevisionCandidato(request);

        Servicio servicio = obtenerServicio(link);
        // El link sigue abierto después de enviar (V197): el candidato puede volver a corregir y
        // re-enviar. Solo el PRIMER envío avisa a gestión y mueve el servicio; los siguientes
        // actualizan los datos en silencio (si no, cada corrección era una notificación nueva).
        boolean primerEnvio = !Boolean.TRUE.equals(link.getUsado());
        guardar(servicio, request, true);

        if (primerEnvio) {
            link.setUsado(true);
            link.setFechaUso(LocalDateTime.now());
            linkCandidatoRepository.save(link);
        }

        log.info("Hoja de vida {}: servicio={}, candidato={}", primerEnvio ? "completada" : "actualizada",
                servicio.getIdServicio(),
                servicio.getCandidato() != null ? servicio.getCandidato().getCedula() : null);

        if (primerEnvio) {
            // El candidato ya entregó su parte: el reloj de la solicitud arranca (la espera cuenta como pausa y corre la fecha de compromiso).
            semaforoTiempoService.cerrarPausas(servicio, com.polygraph.erp.modules.servicios.service.SemaforoTiempoService.MOTIVO_HOJA_DE_VIDA);
            avanzarAProgramandoSiPendiente(servicio);
            notificarFormularioCompletado(servicio);
        }
    }

    /** Al enviar: estado y lugar de cada estudio, temporal de cada empleo y las referencias
     *  (revisión del módulo del candidato). Guardar el avance NO exige nada de esto. */
    private void validarRevisionCandidato(HojaVidaRequest request) {
        if (request.educacion() != null && !Boolean.TRUE.equals(request.sinEstudios())) {
            int n = 0;
            for (EducacionRequest e : request.educacion()) {
                n++;
                if (e.estado() == null || !ESTADOS_ESTUDIO.contains(e.estado())) {
                    throw new ApiException("Estudio #" + n + ": escoja el estado del estudio.", HttpStatus.BAD_REQUEST);
                }
                if (e.enExterior() == null) {
                    throw new ApiException("Estudio #" + n + ": indique si lo realizó en el exterior.", HttpStatus.BAD_REQUEST);
                }
                if (e.enExterior() && (e.paisExterior() == null || e.paisExterior().isBlank())) {
                    throw new ApiException("Estudio #" + n + ": indique el país donde lo realizó.", HttpStatus.BAD_REQUEST);
                }
            }
        }
        if (request.experienciaLaboral() != null) {
            int n = 0;
            for (ExperienciaLaboralRequest ex : request.experienciaLaboral()) {
                n++;
                if (ex.contratadoPorTemporal() == null) {
                    throw new ApiException("Experiencia #" + n + ": indique si lo contrató una empresa de servicios temporales.", HttpStatus.BAD_REQUEST);
                }
                if (ex.contratadoPorTemporal() && (ex.nombreTemporal() == null || ex.nombreTemporal().isBlank())) {
                    throw new ApiException("Experiencia #" + n + ": escriba el nombre de la empresa de servicios temporales.", HttpStatus.BAD_REQUEST);
                }
            }
        }
        if (request.referencias() != null) {
            String propio = soloDigitos(request.celular());
            java.util.Set<String> vistos = new java.util.HashSet<>();
            int n = 0;
            for (ReferenciaPersonalRequest r : request.referencias()) {
                n++;
                String tel = soloDigitos(r.telefono());
                if (!tel.matches("3[0-9]{9}")) {
                    throw new ApiException("Referencia #" + n + ": el celular debe tener 10 dígitos y empezar por 3.", HttpStatus.BAD_REQUEST);
                }
                if (tel.equals(propio)) {
                    throw new ApiException("Referencia #" + n + ": el celular no puede ser el suyo.", HttpStatus.BAD_REQUEST);
                }
                if (!vistos.add(tel)) {
                    throw new ApiException("Referencia #" + n + ": el celular está repetido en otra referencia.", HttpStatus.BAD_REQUEST);
                }
                if ("Menos de 1 año".equals(r.tiempoConocimiento())) {
                    throw new ApiException("Referencia #" + n + ": debe conocerle hace mínimo un (1) año.", HttpStatus.BAD_REQUEST);
                }
                if (!Boolean.TRUE.equals(r.confirmaNoFamiliar()) || !Boolean.TRUE.equals(r.habloUltimos30Dias())) {
                    throw new ApiException("Referencia #" + n + ": debe marcar las dos confirmaciones.", HttpStatus.BAD_REQUEST);
                }
            }
        }
    }

    private static String soloDigitos(String v) {
        if (v == null) return "";
        String d = v.replaceAll("[^0-9]", "");
        return d.length() == 12 && d.startsWith("57") ? d.substring(2) : d;
    }

    /** Al enviar, cada pregunta de declaración activa debe tener respuesta y, si es "Sí", su
     *  explicación. Las preguntas las administra el admin (ItemHojaVida, lista PREGUNTA). */
    private void validarDeclaraciones(HojaVidaRequest request) {
        Map<Integer, RespuestaHojaVidaRequest> porPregunta = new java.util.HashMap<>();
        if (request.respuestas() != null) {
            request.respuestas().stream()
                    .filter(r -> r != null && r.idPregunta() != null)
                    .forEach(r -> porPregunta.put(r.idPregunta(), r));
        }
        for (ItemHojaVida pregunta : itemHojaVidaRepository.findByListaAndActivoTrueOrderByOrdenAscIdAsc(ListaHojaVida.PREGUNTA)) {
            RespuestaHojaVidaRequest r = porPregunta.get(pregunta.getId());
            if (r == null || r.respuesta() == null) {
                throw new ApiException("Responda todas las preguntas de la declaración.", HttpStatus.BAD_REQUEST);
            }
            if (r.respuesta() && (r.detalle() == null || r.detalle().isBlank())) {
                throw new ApiException("Explique su respuesta en cada pregunta que respondió Sí.", HttpStatus.BAD_REQUEST);
            }
        }
    }

    /** Si el candidato termina de diligenciar su hoja de vida mientras el servicio sigue
     *  PENDIENTE, pasa automáticamente a PROGRAMANDO — el gestor ya no tiene que hacerlo a mano
     *  apenas hay información con la que empezar a programar. Se registra en el historial de
     *  estados igual que un cambio manual (mismo mecanismo que SolicitudService.cambiarEstado),
     *  atribuido al gestor asignado al cliente porque el esquema no tiene un usuario "sistema".
     *  La espera del candidato ya no se "descuenta" recalculando la fecha: queda como una pausa del reloj (SemaforoTiempoService). */
    private void avanzarAProgramandoSiPendiente(Servicio servicio) {
        if (servicio.getEstado() != EstadoServicio.PENDIENTE) return;

        Cliente cliente = servicio.getCliente();
        Long idGestor = cliente != null ? cliente.getIdGestor() : null;
        Usuario gestor = idGestor != null ? usuarioRepository.findById(idGestor).orElse(null) : null;
        if (gestor == null) {
            log.warn("Servicio {} completó hoja de vida pero no tiene gestor asignado — no se puede " +
                    "registrar el cambio automático a PROGRAMANDO (queda en PENDIENTE).", servicio.getIdServicio());
            return;
        }

        EstadoServicio anterior = servicio.getEstado();
        servicio.setEstado(EstadoServicio.PROGRAMANDO);

        semaforoTiempoService.recalcular(servicio);

        historialEstadoServicioRepository.save(HistorialEstadoServicio.builder()
                .servicio(servicio)
                .estadoAnterior(anterior)
                .estadoNuevo(EstadoServicio.PROGRAMANDO)
                .usuario(gestor)
                .fechaCambio(LocalDateTime.now())
                .observacion("Cambio automático: el candidato completó su hoja de vida. La espera quedó como pausa del reloj "
                        + "(fecha de compromiso: " + servicio.fechaParaMostrar() + ").")
                .build());

        log.info("Servicio {} pasó automáticamente de PENDIENTE a PROGRAMANDO (candidato completó hoja de vida) — compromiso: {}",
                servicio.getIdServicio(), servicio.fechaParaMostrar());
    }

    private void notificarFormularioCompletado(Servicio servicio) {
        Candidato candidato = servicio.getCandidato();
        String nombre = candidato != null
                ? candidato.getNombres() + (candidato.getApellidos() != null ? " " + candidato.getApellidos() : "")
                : "El candidato";

        LocalDateTime ahora = LocalDateTime.now();
        usuarioRepository.findByRolIn(List.of(Rol.GESTOR, Rol.ADMIN_POLYGRAPH)).stream()
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .forEach(dest -> notificacionRepository.save(Notificacion.builder()
                        .usuario(dest)
                        .tipo("EVALUADO_COMPLETADO")
                        .titulo("El candidato completó su hoja de vida")
                        .mensaje(nombre.trim() + " terminó de diligenciar el formulario del servicio #" + servicio.getIdServicio()
                                + (servicio.getProceso() != null ? " (" + servicio.getProceso().getNombreProceso() + ")" : ""))
                        .leida(false)
                        .fechaCreacion(ahora)
                        .referenciaTipo("SERVICIO")
                        .referenciaId(servicio.getIdServicio().longValue())
                        .build()));
    }

    private void guardar(Servicio servicio, HojaVidaRequest request, boolean marcarCompleto) {
        Candidato candidato = servicio.getCandidato();
        if (candidato == null) {
            throw new ApiException("El servicio no tiene un candidato asociado", HttpStatus.CONFLICT);
        }

        HojaVidaEvaluado hojaVida = obtenerOCrearHojaVida(candidato);

        if (request.idCiudadNacimiento() != null || request.idCiudadResidencia() != null
                || request.idPaisNacimiento() != null || request.idPaisResidencia() != null) {
            Integer idCiudadAnterior = candidato.getIdCiudadResidencia();
            candidato.setIdCiudadNacimiento(request.idCiudadNacimiento());
            candidato.setIdCiudadResidencia(request.idCiudadResidencia());
            if (request.idPaisNacimiento() != null) candidato.setIdPaisNacimiento(request.idPaisNacimiento());
            if (request.idPaisResidencia() != null) candidato.setIdPaisResidencia(request.idPaisResidencia());
            candidatoRepository.save(candidato);

            // El evaluado también puede cambiar su ciudad al llenar su hoja de vida (no solo el
            // gestor) — si eso afecta un recargo por ciudad ya calculado o cobrado, se reconcilia igual.
            boolean cambioCiudad = request.idCiudadResidencia() != null
                    && !request.idCiudadResidencia().equals(idCiudadAnterior);
            if (cambioCiudad && servicio.getCliente() != null && servicio.getProceso() != null
                    && servicio.getCliente().getTipoCliente() == TipoCliente.PREPAGO) {
                recargoPendienteService.reconciliarPorCambioCiudad(
                        servicio.getCliente(), candidato, servicio.getProceso(), request.idCiudadResidencia());
            }
        }

        // Expedición del documento: solo se escribe si el candidato la diligenció (no borra la que
        // haya cargado el gestor).
        if (request.fechaExpedicion() != null || request.idCiudadExpedicion() != null) {
            if (request.fechaExpedicion() != null) candidato.setFechaExpedicion(request.fechaExpedicion());
            if (request.idCiudadExpedicion() != null) candidato.setIdCiudadExpedicion(request.idCiudadExpedicion());
            candidatoRepository.save(candidato);
        }

        if (request.pasoActual() != null) {
            hojaVida.setPasoActual(request.pasoActual());
        }
        // La autorización se acepta en su propia pantalla (autorizarTratamientoDatos); guardar la
        // hoja de vida nunca la quita.
        if (Boolean.TRUE.equals(request.autorizacionDatos()) && !Boolean.TRUE.equals(hojaVida.getAutorizacionDatos())) {
            hojaVida.setAutorizacionDatos(true);
            if (hojaVida.getFechaAutorizacion() == null) hojaVida.setFechaAutorizacion(LocalDateTime.now());
        }
        hojaVida.setFechaNacimiento(request.fechaNacimiento());
        hojaVida.setLugarNacimiento(request.lugarNacimiento());
        hojaVida.setEstadoCivil(request.estadoCivil());
        boolean sinEstudios = Boolean.TRUE.equals(request.sinEstudios());
        hojaVida.setSinEstudios(sinEstudios);
        hojaVida.setNivelEducativo(nivelEducativoDe(request, sinEstudios));
        hojaVida.setDireccion(request.direccion());
        hojaVida.setBarrio(request.barrio());
        if (request.idBarrio() != null) hojaVida.setIdBarrio(request.idBarrio());
        hojaVida.setEstrato(request.estrato());
        hojaVida.setEmail(request.email());
        hojaVida.setCelular(request.celular());
        hojaVida.setRh(request.rh());
        hojaVida.setLibretaMilitar(request.libretaMilitar());
        // El número de visa/pasaporte solo se conserva si respondió "Sí"
        hojaVida.setTieneVisa(request.tieneVisa());
        hojaVida.setVisa(Boolean.FALSE.equals(request.tieneVisa()) ? null : request.visa());
        hojaVida.setVisaPais(Boolean.FALSE.equals(request.tieneVisa()) ? null : request.visaPais());
        hojaVida.setTienePasaporte(request.tienePasaporte());
        hojaVida.setPasaporte(Boolean.FALSE.equals(request.tienePasaporte()) ? null : request.pasaporte());
        hojaVida.setDireccionAdicional(request.direccionAdicional());
        hojaVida.setFondoPensiones(request.fondoPensiones());
        hojaVida.setEps(request.eps());
        hojaVida.setTelefonoFijo(request.telefonoFijo());

        // Reemplazar educación
        hojaVida.getEducacion().clear();
        if (request.educacion() != null && !sinEstudios) {
            request.educacion().forEach(e -> hojaVida.getEducacion().add(
                    EducacionEvaluado.builder()
                            .hojaVidaEvaluado(hojaVida)
                            .nivel(e.nivel())
                            .institucion(e.institucion())
                            .titulo(e.titulo())
                            .fechaInicio(e.fechaInicio())
                            .fechaFin(e.fechaFin())
                            .enCurso(e.enCurso())
                            .ciudad(e.ciudad())
                            .estado(e.estado())
                            // "En curso" es el estado; enCurso se deriva para lo que ya lo lee
                            .enCurso(ESTADO_EN_CURSO.equals(e.estado()) || (e.estado() == null && Boolean.TRUE.equals(e.enCurso())))
                            .enExterior(e.enExterior())
                            .paisExterior(Boolean.TRUE.equals(e.enExterior()) ? e.paisExterior() : null)
                            .build()));
        }

        // Reemplazar experiencia laboral
        hojaVida.getExperienciaLaboral().clear();
        if (request.experienciaLaboral() != null) {
            request.experienciaLaboral().forEach(ex -> hojaVida.getExperienciaLaboral().add(
                    ExperienciaLaboralEvaluado.builder()
                            .hojaVidaEvaluado(hojaVida)
                            .empresa(ex.empresa())
                            .cargo(ex.cargo())
                            .fechaInicio(ex.fechaInicio())
                            .fechaFin(ex.fechaFin())
                            .laboraActualmente(ex.laboraActualmente())
                            .ciudad(ex.ciudad())
                            .telefonoEmpresa(ex.telefonoEmpresa())
                            .motivoRetiro(ex.motivoRetiro())
                            .nombreJefe(ex.nombreJefe())
                            .cargoJefe(ex.cargoJefe())
                            .tipoContrato(ex.tipoContrato())
                            .salario(ex.salario())
                            .llamadosAtencion(ex.llamadosAtencion())
                            .contratadoPorTemporal(ex.contratadoPorTemporal())
                            .nombreTemporal(Boolean.TRUE.equals(ex.contratadoPorTemporal()) ? ex.nombreTemporal() : null)
                            .build()));
        }

        // Recalcular inactividades
        hojaVida.getInactividades().clear();
        hojaVidaRepository.save(hojaVida);

        List<InactividadLaboralEvaluado> inactividades = historialLaboralService
                .calcularInactividades(hojaVida, hojaVida.getExperienciaLaboral());
        Map<String, String> justificaciones = request.justificacionesInactividad() != null
                ? request.justificacionesInactividad() : Map.of();
        Map<String, HojaVidaRequest.DetalleInactividad> detalles = request.detallesInactividad() != null
                ? request.detallesInactividad() : Map.of();
        inactividades.forEach(inact -> {
            String clave = HistorialLaboralService.clave(inact, hojaVida.getExperienciaLaboral());
            inact.setJustificacion(justificaciones.get(clave));
            HojaVidaRequest.DetalleInactividad d = detalles.get(clave);
            if (d != null) {
                inact.setActividad(d.actividad());
                inact.setQuienConfirma(d.quienConfirma());
            }
        });
        hojaVida.getInactividades().addAll(inactividades);

        // Reemplazar referencias
        hojaVida.getReferencias().clear();
        if (request.referencias() != null) {
            request.referencias().forEach(r -> hojaVida.getReferencias().add(
                    ReferenciaPersonalEvaluado.builder()
                            .hojaVidaEvaluado(hojaVida)
                            .nombre(r.nombre())
                            .parentesco(r.parentesco())
                            .telefono(r.telefono())
                            .tiempoConocimiento(r.tiempoConocimiento())
                            .confirmaNoFamiliar(r.confirmaNoFamiliar())
                            .habloUltimos30Dias(r.habloUltimos30Dias())
                            .build()));
        }

        // Reemplazar declaraciones (null = el portal no las mandó: se dejan como están)
        if (request.respuestas() != null) {
            Map<Integer, ItemHojaVida> preguntas = new java.util.HashMap<>();
            itemHojaVidaRepository.findByListaOrderByOrdenAscIdAsc(ListaHojaVida.PREGUNTA)
                    .forEach(p -> preguntas.put(p.getId(), p));
            hojaVida.getRespuestas().clear();
            request.respuestas().stream()
                    .filter(r -> r != null && r.respuesta() != null && preguntas.containsKey(r.idPregunta()))
                    .forEach(r -> hojaVida.getRespuestas().add(RespuestaHojaVida.builder()
                            .hojaVidaEvaluado(hojaVida)
                            .idItem(r.idPregunta())
                            .pregunta(preguntas.get(r.idPregunta()).getNombre())
                            .respuesta(r.respuesta())
                            .detalle(r.respuesta() && r.detalle() != null && !r.detalle().isBlank() ? r.detalle().trim() : null)
                            .build()));
        }

        int progreso = calcularProgreso(hojaVida);
        hojaVida.setProgresoPorcentaje(progreso);

        if (marcarCompleto) {
            hojaVida.setCompletado(true);
            hojaVida.setFechaCompletado(LocalDateTime.now());
        }

        hojaVidaRepository.save(hojaVida);
    }

    /** El nivel educativo ya no se escribe: es el estudio más alto que registró el candidato. Si
     *  ninguno es de la lista actual (datos anteriores), se conserva el que venía. */
    private static String nivelEducativoDe(HojaVidaRequest request, boolean sinEstudios) {
        if (sinEstudios) return SIN_ESTUDIOS;
        if (request.educacion() == null) return request.nivelEducativo();
        return request.educacion().stream()
                .map(EducacionRequest::nivel)
                .filter(NIVELES_EDUCATIVOS::contains)
                .max(java.util.Comparator.comparingInt(NIVELES_EDUCATIVOS::indexOf))
                .orElse(request.nivelEducativo());
    }

    private int calcularProgreso(HojaVidaEvaluado hv) {
        int puntos = 0;
        if (Boolean.TRUE.equals(hv.getAutorizacionDatos())) puntos += 20;
        if (hv.getFechaNacimiento() != null) puntos += 20;
        if (!hv.getEducacion().isEmpty()) puntos += 20;
        if (!hv.getExperienciaLaboral().isEmpty()) puntos += 20;
        if (!hv.getReferencias().isEmpty()) puntos += 20;
        return puntos;
    }

    private HojaVidaEvaluado obtenerOCrearHojaVida(Candidato candidato) {
        return hojaVidaRepository
                .findByCandidato_IdCandidato(candidato.getIdCandidato())
                .orElseGet(() -> hojaVidaRepository.save(HojaVidaEvaluado.builder()
                        .candidato(candidato)
                        .autorizacionDatos(false)
                        .completado(false)
                        .progresoPorcentaje(0)
                        .build()));
    }

    // ── Documentos ─────────────────────────────────────────────────

    public DocumentoEvaluadoResponse subirDocumento(String token, String tipoDocumento, MultipartFile archivo) {
        if (tipoDocumento == null || tipoDocumento.isBlank()) {
            throw new ApiException("El tipo de documento es obligatorio", HttpStatus.BAD_REQUEST);
        }
        LinkCandidato link = obtenerLinkValido(token);
        Servicio servicio = obtenerServicio(link);
        Candidato candidato = servicio.getCandidato();
        if (candidato == null) {
            throw new ApiException("El servicio no tiene un candidato asociado", HttpStatus.CONFLICT);
        }
        exigirAutorizacion(servicio);
        exigirHojaVidaEnviada(servicio);
        validadorArchivo.validar(archivo);
        HojaVidaEvaluado hojaVida = obtenerOCrearHojaVida(candidato);

        // Reemplaza el documento existente del mismo tipo, si lo hay (un archivo activo por tipo).
        // Uno ya APROBADO no se puede cambiar desde el link: se perdería lo que el equipo revisó.
        // El reemplazo (ej. de uno DEVUELTO) nace PENDIENTE otra vez.
        documentoRepository.findByHojaVidaEvaluado_IdAndTipoDocumento(hojaVida.getId(), tipoDocumento)
                .ifPresent(existente -> {
                    exigirNoAprobado(existente);
                    almacenamientoService.eliminar(existente.getRutaArchivo());
                    documentoRepository.delete(existente);
                    documentoRepository.flush();
                });

        String rutaRelativa = almacenamientoService.guardar(archivo, "evaluados", String.valueOf(servicio.getIdServicio()));
        DocumentoEvaluado documento = DocumentoEvaluado.builder()
                .hojaVidaEvaluado(hojaVida)
                .tipoDocumento(tipoDocumento)
                .nombreArchivo(archivo.getOriginalFilename())
                .rutaArchivo(rutaRelativa)
                .fechaCarga(LocalDateTime.now())
                .build();
        documentoRepository.save(documento);
        log.info("Documento '{}' cargado — servicio={}, candidato={}", tipoDocumento, servicio.getIdServicio(), candidato.getCedula());
        return DocumentoEvaluadoResponse.from(documento);
    }

    @Transactional(readOnly = true)
    public List<DocumentoEvaluadoResponse> listarDocumentos(String token) {
        LinkCandidato link = obtenerLinkValido(token);
        Servicio servicio = obtenerServicio(link);
        Candidato candidato = servicio.getCandidato();
        if (candidato == null) return List.of();

        return hojaVidaRepository.findByCandidato_IdCandidato(candidato.getIdCandidato())
                .map(hv -> documentoRepository.findByHojaVidaEvaluado_IdOrderByFechaCargaDesc(hv.getId()).stream()
                        .map(DocumentoEvaluadoResponse::from)
                        .toList())
                .orElse(List.of());
    }

    public void eliminarDocumento(String token, Long idDocumento) {
        LinkCandidato link = obtenerLinkValido(token);
        Servicio servicio = obtenerServicio(link);
        Candidato candidato = servicio.getCandidato();

        DocumentoEvaluado documento = documentoRepository.findById(idDocumento)
                .orElseThrow(() -> new ApiException("Documento no encontrado", HttpStatus.NOT_FOUND));

        boolean pertenece = candidato != null
                && documento.getHojaVidaEvaluado().getCandidato() != null
                && documento.getHojaVidaEvaluado().getCandidato().getIdCandidato().equals(candidato.getIdCandidato());
        if (!pertenece) {
            throw new ApiException("Documento no encontrado", HttpStatus.NOT_FOUND);
        }
        exigirNoAprobado(documento);

        almacenamientoService.eliminar(documento.getRutaArchivo());
        documentoRepository.delete(documento);
    }

    private static void exigirNoAprobado(DocumentoEvaluado documento) {
        if (documento.getEstadoRevision() == EstadoRevisionDocumento.APROBADO) {
            throw new ApiException("Este documento ya fue aprobado y no se puede cambiar. Si necesitas corregirlo, "
                    + "comunícate con quien te envió el link.", HttpStatus.CONFLICT);
        }
    }

    /** Servicio del link, si el candidato todavía tiene acceso — lo usa también
     *  PortalCandidatoService (citas y sugerencias). */
    @Transactional(readOnly = true)
    public Servicio servicioConAcceso(String token) {
        return obtenerServicio(obtenerLinkValido(token));
    }

    /** Desde V197 el acceso NO vence a las 36 h ni al enviar: el candidato sigue editando, viendo
     *  sus citas y el estado de sus documentos hasta que el servicio se cierre (ver EstadoLink). */
    private LinkCandidato obtenerLinkValido(String token) {
        LinkCandidato link = linkCandidatoRepository.findByToken(token)
                .orElseThrow(() -> new ApiException("Link no encontrado o inválido", HttpStatus.NOT_FOUND));

        switch (EstadoLink.de(link)) {
            case REVOCADO -> throw new ApiException(
                    "Este link fue desactivado. Si necesitas acceso, comunícate con quien te lo envió.", HttpStatus.GONE);
            case BLOQUEADO -> throw new ApiException(
                    "Este link fue bloqueado por seguridad. Comunícate con quien te lo envió.", HttpStatus.GONE);
            case CERRADO -> throw new ApiException(
                    "Este estudio ya finalizó; el link ya no está disponible. ¡Gracias por tu tiempo!", HttpStatus.GONE);
            default -> { }
        }
        return link;
    }

    private Servicio obtenerServicio(LinkCandidato link) {
        if (link.getServicio() == null) {
            throw new ApiException("Servicio asociado no encontrado", HttpStatus.NOT_FOUND);
        }
        return link.getServicio();
    }
}
