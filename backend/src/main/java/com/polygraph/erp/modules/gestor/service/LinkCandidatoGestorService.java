package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.auth.service.EmailService;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.entity.DocumentoEvaluado;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.evaluados.repository.DocumentoEvaluadoRepository;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService;
import com.polygraph.erp.modules.gestor.dto.GenerarLinkRequest;
import com.polygraph.erp.modules.gestor.dto.LinkCandidatoResponse;
import com.polygraph.erp.modules.gestor.dto.LinkEstadisticasResponse;
import com.polygraph.erp.modules.gestor.dto.ResumenEvaluadoResponse;
import com.polygraph.erp.modules.servicios.entity.EstadoLink;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.utils.AccesoGestor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkCandidatoGestorService {

    private final LinkCandidatoRepository      linkRepository;
    private final ServicioRepository           servicioRepository;
    private final UsuarioRepository            usuarioRepository;
    private final HojaVidaEvaluadoRepository   hojaVidaRepository;
    private final CiudadRepository             ciudadRepository;
    private final DocumentoEvaluadoRepository  documentoRepository;
    private final AlmacenamientoDocumentosService almacenamientoService;
    private final EmailService                 emailService;

    // Solo queda como dato histórico en fecha_expiracion: desde V197 el acceso del candidato NO
    // vence por tiempo, sino cuando el servicio se cierra (ver EstadoLink).
    private static final int   HORAS_EXPIRACION = 36;
    private static final java.util.Set<com.polygraph.erp.shared.enums.EstadoServicio> CERRADOS = EstadoLink.SERVICIO_CERRADO;

    // ── Acceso: un GESTOR solo ve y opera links de servicios de SUS clientes (AccesoGestor) ──

    private Usuario actor(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.UNAUTHORIZED));
    }

    /** El servicio, si quien pide es su gestor (403 si es de otro gestor). */
    private Servicio servicioAccesible(Integer idServicio, Usuario actor) {
        Servicio servicio = servicioRepository.findById(idServicio)
                .orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
        AccesoGestor.validar(servicio, actor);
        return servicio;
    }

    /** El link, si su servicio es de un cliente de quien pide (403 si es de otro gestor). */
    private LinkCandidato linkAccesible(Long id, Usuario actor) {
        LinkCandidato link = linkRepository.findById(id)
                .orElseThrow(() -> new ApiException("Link no encontrado", HttpStatus.NOT_FOUND));
        AccesoGestor.validar(link.getServicio(), actor);
        return link;
    }

    @Transactional(readOnly = true)
    public Page<LinkCandidatoResponse> listar(String estado, Pageable pageable, String emailActual) {
        LocalDateTime ahora = LocalDateTime.now();
        Long idGestor = actor(emailActual).getIdUsuario();

        Page<LinkCandidato> pagina = switch (estado != null ? estado.toUpperCase() : "") {
            case "PENDIENTE" -> linkRepository.findConAcceso(idGestor, CERRADOS, false, pageable);
            case "USADO"     -> linkRepository.findConAcceso(idGestor, CERRADOS, true, pageable);
            // "EXPIRADO" se conserva como alias por compatibilidad: ya no vence por tiempo.
            case "CERRADO", "EXPIRADO" -> linkRepository.findCerrados(idGestor, CERRADOS, pageable);
            case "REVOCADO"  -> linkRepository.findRevocados(idGestor, pageable);
            case "BLOQUEADO" -> linkRepository.findBloqueados(idGestor, pageable);
            default          -> linkRepository.findDelGestor(idGestor, pageable);
        };

        // Carga en bloque para evitar N+1
        List<Integer> ids = pagina.stream()
                .map(l -> l.getServicio() != null ? l.getServicio().getIdServicio() : null)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Integer, Servicio> serviciosMap = servicioRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(Servicio::getIdServicio, Function.identity()));

        return pagina.map(l -> toResponse(l, ahora,
                l.getServicio() != null ? serviciosMap.get(l.getServicio().getIdServicio()) : null));
    }

    /** Links que requieren atención del gestor: bloqueados primero, luego los pendientes que más
     *  llevan esperando a que el candidato envíe (ya no vencen por tiempo). */
    @Transactional(readOnly = true)
    public List<LinkCandidatoResponse> obtenerLinksPorAtender(Long idGestor, int limite) {
        LocalDateTime ahora = LocalDateTime.now();

        List<LinkCandidato> bloqueados = linkRepository.findBloqueados(idGestor, PageRequest.of(0, limite)).getContent();
        int restantes = Math.max(0, limite - bloqueados.size());
        List<LinkCandidato> pendientes = restantes > 0
                ? linkRepository.findPendientesMasAntiguos(idGestor, CERRADOS, PageRequest.of(0, restantes))
                : List.of();

        List<LinkCandidato> combinados = new java.util.ArrayList<>(bloqueados);
        combinados.addAll(pendientes);

        List<Integer> ids = combinados.stream()
                .map(l -> l.getServicio() != null ? l.getServicio().getIdServicio() : null)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Integer, Servicio> serviciosMap = servicioRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(Servicio::getIdServicio, Function.identity()));

        return combinados.stream()
                .map(l -> toResponse(l, ahora,
                        l.getServicio() != null ? serviciosMap.get(l.getServicio().getIdServicio()) : null))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<LinkCandidatoResponse> obtenerPorServicio(Integer idServicio, String emailActual) {
        LocalDateTime ahora = LocalDateTime.now();
        Servicio servicio = servicioAccesible(idServicio, actor(emailActual));
        return linkRepository.findTopByServicio_IdServicioOrderByFechaCreacionDesc(idServicio)
                .map(l -> toResponse(l, ahora, servicio));
    }

    /** Resumen de solo lectura de la hoja de vida que el evaluado diligenció, para el gestor. */
    @Transactional(readOnly = true)
    public ResumenEvaluadoResponse obtenerResumenPorServicio(Integer idServicio, String emailActual) {
        Servicio servicio = servicioAccesible(idServicio, actor(emailActual));

        Candidato candidato = servicio.getCandidato();
        if (candidato == null) {
            throw new ApiException("El servicio no tiene un candidato asociado", HttpStatus.NOT_FOUND);
        }

        HojaVidaEvaluado hv = hojaVidaRepository.findByCandidato_IdCandidato(candidato.getIdCandidato())
                .orElseThrow(() -> new ApiException(
                        "El candidato aún no ha aceptado la autorización de datos ni diligenciado su hoja de vida",
                        HttpStatus.NOT_FOUND));

        String ciudadNacimiento = nombreCiudad(candidato.getIdCiudadNacimiento());
        String ciudadResidencia = nombreCiudad(candidato.getIdCiudadResidencia());

        return new ResumenEvaluadoResponse(
                candidato.getNombres(), candidato.getApellidos(), candidato.getCedula(), candidato.getTipoDocumento(),
                Boolean.TRUE.equals(hv.getAutorizacionDatos()), hv.getFechaAutorizacion(),
                hv.getCompletado(), hv.getFechaCompletado(), hv.getProgresoPorcentaje(), hv.getPasoActual(),
                hv.getFechaNacimiento(), hv.getEstadoCivil(), hv.getRh(), hv.getNivelEducativo(),
                ciudadNacimiento, ciudadResidencia,
                hv.getDireccion(), hv.getBarrio(), hv.getEstrato(),
                hv.getEmail(), hv.getCelular(), hv.getTelefonoFijo(),
                hv.getLibretaMilitar(), hv.getVisa(), hv.getPasaporte(), hv.getFondoPensiones(), hv.getEps(),
                candidato.getFechaExpedicion(), nombreCiudad(candidato.getIdCiudadExpedicion()),
                hv.getDireccionAdicional(), hv.getTieneVisa(), hv.getVisaPais(), hv.getTienePasaporte(),
                Boolean.TRUE.equals(hv.getSinEstudios()),
                hv.getEducacion().stream()
                        .map(e -> new ResumenEvaluadoResponse.EducacionItem(
                                e.getNivel(), e.getInstitucion(), e.getTitulo(),
                                e.getFechaInicio(), e.getFechaFin(), e.getEnCurso(), e.getCiudad(),
                                e.getEstado(), e.getEnExterior(), e.getPaisExterior()))
                        .toList(),
                hv.getExperienciaLaboral().stream()
                        .map(ex -> new ResumenEvaluadoResponse.ExperienciaItem(
                                ex.getEmpresa(), ex.getCargo(), ex.getFechaInicio(), ex.getFechaFin(),
                                ex.getLaboraActualmente(), ex.getCiudad(), ex.getTelefonoEmpresa(),
                                ex.getMotivoRetiro(), ex.getNombreJefe(), ex.getCargoJefe(),
                                ex.getTipoContrato(), ex.getSalario(), ex.getLlamadosAtencion(),
                                ex.getContratadoPorTemporal(), ex.getNombreTemporal()))
                        .toList(),
                hv.getInactividades().stream()
                        .map(i -> new ResumenEvaluadoResponse.InactividadItem(
                                i.getFechaInicio(), i.getFechaFin(), i.getDiasInactivo(),
                                i.getJustificacion(), i.getRequiereCuestionario(),
                                i.getActividad(), i.getQuienConfirma()))
                        .toList(),
                hv.getReferencias().stream()
                        .map(r -> new ResumenEvaluadoResponse.ReferenciaItem(
                                r.getNombre(), r.getParentesco(), r.getTelefono(), r.getTiempoConocimiento(),
                                r.getConfirmaNoFamiliar(), r.getHabloUltimos30Dias()))
                        .toList(),
                hv.getDocumentos().stream()
                        .map(d -> new ResumenEvaluadoResponse.DocumentoItem(
                                d.getId(), d.getTipoDocumento(), d.getNombreArchivo(), d.getFechaCarga()))
                        .toList(),
                hv.getRespuestas().stream()
                        .map(r -> new ResumenEvaluadoResponse.DeclaracionItem(
                                r.getPregunta(), r.getRespuesta(), r.getDetalle()))
                        .toList()
        );
    }

    public record DescargaDocumento(Resource recurso, String nombreArchivo, String tipoContenido) {}

    @Transactional(readOnly = true)
    public DescargaDocumento descargarDocumento(Integer idServicio, Long idDocumento, String emailActual) {
        Servicio servicio = servicioAccesible(idServicio, actor(emailActual));
        Candidato candidato = servicio.getCandidato();

        DocumentoEvaluado documento = documentoRepository.findById(idDocumento)
                .orElseThrow(() -> new ApiException("Documento no encontrado", HttpStatus.NOT_FOUND));

        boolean pertenece = candidato != null
                && documento.getHojaVidaEvaluado().getCandidato() != null
                && documento.getHojaVidaEvaluado().getCandidato().getIdCandidato().equals(candidato.getIdCandidato());
        if (!pertenece) {
            throw new ApiException("Documento no encontrado", HttpStatus.NOT_FOUND);
        }

        Resource recurso = almacenamientoService.cargarComoResource(documento.getRutaArchivo());
        String tipoContenido = almacenamientoService.tipoContenido(documento.getNombreArchivo());
        return new DescargaDocumento(recurso, documento.getNombreArchivo(), tipoContenido);
    }

    private String nombreCiudad(Integer idCiudad) {
        return idCiudad != null
                ? ciudadRepository.findById(idCiudad).map(Ciudad::getNombreCiudad).orElse(null)
                : null;
    }

    @Transactional(readOnly = true)
    public List<LinkCandidatoResponse> listarHistorialPorServicio(Integer idServicio, String emailActual) {
        LocalDateTime ahora = LocalDateTime.now();
        Servicio servicio = servicioAccesible(idServicio, actor(emailActual));
        return linkRepository.findByServicio_IdServicioOrderByFechaCreacionDesc(idServicio).stream()
                .map(l -> toResponse(l, ahora, servicio))
                .toList();
    }

    @Transactional(readOnly = true)
    public LinkEstadisticasResponse estadisticas(String emailActual) {
        Long idGestor = actor(emailActual).getIdUsuario();
        long total      = linkRepository.contarDelGestor(idGestor);
        long pendientes = linkRepository.contarConAcceso(idGestor, CERRADOS, false);
        long usados     = linkRepository.contarConAcceso(idGestor, CERRADOS, true);
        // "expirados" = ya sin acceso por cierre del servicio o revocación (no hay vencimiento por tiempo).
        long expirados  = linkRepository.contarCerrados(idGestor, CERRADOS) + linkRepository.contarRevocados(idGestor);
        long bloqueados = linkRepository.contarBloqueados(idGestor);
        return new LinkEstadisticasResponse(total, pendientes, usados, expirados, bloqueados);
    }

    @Transactional
    public LinkCandidatoResponse generar(GenerarLinkRequest request, String emailActual, String ipOrigen) {
        Usuario generadoPor = actor(emailActual);
        Servicio servicio = servicioAccesible(request.idServicio(), generadoPor);

        LocalDateTime ahora = LocalDateTime.now();

        if (CERRADOS.contains(servicio.getEstado())) {
            throw new ApiException("El servicio #" + servicio.getIdServicio() + " ya está cerrado ("
                    + servicio.getEstado() + "): no tiene sentido un link nuevo.", HttpStatus.CONFLICT);
        }
        // UN SOLO link por servicio (V201, índice único): generar uno nuevo REEMPLAZA el anterior —
        // se elimina y su token deja de funcionar. Lo del candidato (hoja de vida, documentos,
        // autorización) no está atado al link sino al candidato, así que no se pierde nada.
        // Si ya había enviado la hoja de vida, el link nuevo nace como "enviado" (usado).
        List<LinkCandidato> anteriores = linkRepository.findByServicio_IdServicioOrderByFechaCreacionDesc(servicio.getIdServicio());
        LinkCandidato ultimo = anteriores.isEmpty() ? null : anteriores.get(0);
        if (!anteriores.isEmpty()) {
            linkRepository.deleteAll(anteriores);
            linkRepository.flush(); // antes de insertar el nuevo: el índice único no admite dos
            log.info("Link(s) anterior(es) del servicio {} eliminados al generar uno nuevo: {}",
                    servicio.getIdServicio(), anteriores.size());
        }

        LinkCandidato link = LinkCandidato.builder()
                .servicio(servicio)
                .token(generarHex64())
                .fechaCreacion(ahora)
                .fechaExpiracion(ahora.plusHours(HORAS_EXPIRACION))
                .usado(ultimo != null && Boolean.TRUE.equals(ultimo.getUsado()))
                .fechaUso(ultimo != null ? ultimo.getFechaUso() : null)
                .intentosFallidos((short) 0)
                .ipOrigen(ipOrigen)
                .generadoPor(generadoPor)
                .build();

        LinkCandidato guardado = linkRepository.save(link);
        log.info("Link generado — servicio={}, por={}, reemplazó={}", servicio.getIdServicio(), emailActual, !anteriores.isEmpty());
        return toResponse(guardado, ahora, servicio);
    }

    @Transactional(readOnly = true)
    public void enviarCorreo(Long id, String emailActual) {
        LinkCandidato link = linkAccesible(id, actor(emailActual));

        LocalDateTime ahora = LocalDateTime.now();
        EstadoLink estado = EstadoLink.de(link);
        if (!estado.conAcceso()) {
            throw new ApiException(
                    "Solo se puede enviar por correo un link activo (actual: " + estado + ")",
                    HttpStatus.CONFLICT);
        }

        Servicio servicio = link.getServicio() != null
                ? servicioRepository.findById(link.getServicio().getIdServicio()).orElse(null)
                : null;
        if (servicio == null || servicio.getCandidato() == null) {
            throw new ApiException("El link no tiene un candidato asociado", HttpStatus.CONFLICT);
        }

        String destinatario = servicio.getCandidato().getEmailPrincipal();
        if (destinatario == null || destinatario.isBlank()) {
            throw new ApiException("El candidato no tiene un correo electrónico registrado", HttpStatus.CONFLICT);
        }

        String nombre = servicio.getCandidato().getNombres() + " " +
                (servicio.getCandidato().getApellidos() != null ? servicio.getCandidato().getApellidos() : "");
        String proceso = servicio.getProceso() != null ? servicio.getProceso().getNombreProceso() : null;

        emailService.enviarLinkEvaluado(destinatario, nombre.trim(), link.getToken(), proceso);
        log.info("Correo de link enviado — servicio={}, destinatario={}", servicio.getIdServicio(), destinatario);
    }

    @Transactional
    public void revocar(Long id, String emailActual) {
        LinkCandidato link = linkAccesible(id, actor(emailActual));
        if (Boolean.TRUE.equals(link.getRevocado())) {
            throw new ApiException("El link ya está revocado", HttpStatus.CONFLICT);
        }
        // Antes marcaba usado = true; desde V197 "usado" significa "formulario enviado" y NO cierra
        // el acceso, así que la revocación va en su propia marca.
        link.setRevocado(true);
        log.info("Link {} revocado manualmente", id);
    }

    @Transactional
    public void eliminar(Long id, String emailActual) {
        LinkCandidato link = linkAccesible(id, actor(emailActual));
        EstadoLink estado = EstadoLink.de(link);
        if (estado.conAcceso()) {
            throw new ApiException(
                    "No puedes eliminar un link activo (el candidato todavía entra con él). Revócalo primero.",
                    HttpStatus.CONFLICT);
        }
        linkRepository.delete(link);
        log.info("Link {} eliminado — estado previo: {}", id, estado);
    }

    @Transactional
    public LinkCandidatoResponse revertir(Long id, String emailActual) {
        LinkCandidato link = linkAccesible(id, actor(emailActual));
        if (EstadoLink.de(link) != EstadoLink.USADO) {
            throw new ApiException("Solo se puede revertir un link enviado y todavía activo", HttpStatus.CONFLICT);
        }
        LocalDateTime ahora = LocalDateTime.now();
        link.setUsado(false);
        link.setFechaUso(null);
        link.setIntentosFallidos((short) 0);

        Servicio s = link.getServicio() != null
                ? servicioRepository.findById(link.getServicio().getIdServicio()).orElse(null)
                : null;

        // El candidato va a volver a diligenciar el formulario: mientras lo hace, ya no
        // debe figurar como "Completado" (evita que el gestor vea el estado viejo mientras edita).
        if (s != null && s.getCandidato() != null) {
            hojaVidaRepository.findByCandidato_IdCandidato(s.getCandidato().getIdCandidato())
                    .ifPresent(hv -> {
                        hv.setCompletado(false);
                        hv.setFechaCompletado(null);
                    });
        }

        log.info("Link {} revertido a PENDIENTE — evaluado puede volver a diligenciar", id);
        return toResponse(link, ahora, s);
    }

    private String generarHex64() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String resolverEstado(LinkCandidato l, LocalDateTime ahora) {
        return EstadoLink.de(l).name();
    }

    private LinkCandidatoResponse toResponse(LinkCandidato l, LocalDateTime ahora, Servicio s) {
        String token     = l.getToken();
        String mascarado = (token != null && token.length() > 8)
                ? "••••••••" + token.substring(token.length() - 8)
                : token;

        String generadoPorNombre = null;
        if (l.getGeneradoPor() != null) {
            Usuario u = l.getGeneradoPor();
            generadoPorNombre = u.getNombre() + (u.getApellido() != null ? " " + u.getApellido() : "");
        }

        Integer progresoFormulario = null;
        Integer pasoActual = null;
        if (s != null && s.getCandidato() != null) {
            Optional<HojaVidaEvaluado> hojaVida = hojaVidaRepository
                    .findByCandidato_IdCandidato(s.getCandidato().getIdCandidato());
            progresoFormulario = hojaVida.map(HojaVidaEvaluado::getProgresoPorcentaje).orElse(null);
            pasoActual = hojaVida.map(HojaVidaEvaluado::getPasoActual).orElse(null);
        }

        return new LinkCandidatoResponse(
                l.getId(),
                l.getServicio() != null ? l.getServicio().getIdServicio() : null,
                s != null && s.getCandidato() != null ? s.getCandidato().getNombres()   : null,
                s != null && s.getCandidato() != null ? s.getCandidato().getApellidos() : null,
                s != null && s.getCandidato() != null ? s.getCandidato().getCedula()    : null,
                s != null ? s.getCargo()             : null,
                resolverEstado(l, ahora),
                mascarado,
                token,
                l.getFechaCreacion(),
                l.getFechaExpiracion(),
                l.getFechaUso(),
                l.getIpOrigen(),
                l.getFechaPrimerIngreso(),
                progresoFormulario,
                pasoActual,
                l.getIntentosFallidos(),
                generadoPorNombre,
                s != null && s.getProceso() != null ? s.getProceso().getNombreProceso() : null
        );
    }
}
