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
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
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

    private static final int   HORAS_EXPIRACION = 36;
    private static final short MAX_INTENTOS     = 3;

    @Transactional(readOnly = true)
    public Page<LinkCandidatoResponse> listar(String estado, Pageable pageable) {
        LocalDateTime ahora = LocalDateTime.now();

        Page<LinkCandidato> pagina = switch (estado != null ? estado.toUpperCase() : "") {
            case "PENDIENTE" -> linkRepository.findPendientes(ahora, pageable);
            case "USADO"     -> linkRepository.findUsados(pageable);
            case "EXPIRADO"  -> linkRepository.findExpirados(ahora, pageable);
            case "BLOQUEADO" -> linkRepository.findBloqueados(pageable);
            default          -> linkRepository.findAllByOrderByFechaCreacionDesc(pageable);
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

    /** Links que requieren atención del gestor: bloqueados primero, luego pendientes próximos a expirar. */
    @Transactional(readOnly = true)
    public List<LinkCandidatoResponse> obtenerLinksPorAtender(int limite) {
        LocalDateTime ahora = LocalDateTime.now();

        List<LinkCandidato> bloqueados = linkRepository.findBloqueados(PageRequest.of(0, limite)).getContent();
        int restantes = Math.max(0, limite - bloqueados.size());
        List<LinkCandidato> pendientes = restantes > 0
                ? linkRepository.findPendientesOrderByExpiracionAsc(ahora, PageRequest.of(0, restantes))
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
    public Optional<LinkCandidatoResponse> obtenerPorServicio(Integer idServicio) {
        LocalDateTime ahora = LocalDateTime.now();
        return linkRepository.findTopByServicio_IdServicioOrderByFechaCreacionDesc(idServicio)
                .map(l -> toResponse(l, ahora,
                        servicioRepository.findById(idServicio).orElse(null)));
    }

    /** Resumen de solo lectura de la hoja de vida que el evaluado diligenció, para el gestor. */
    @Transactional(readOnly = true)
    public ResumenEvaluadoResponse obtenerResumenPorServicio(Integer idServicio) {
        Servicio servicio = servicioRepository.findById(idServicio)
                .orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));

        Candidato candidato = servicio.getCandidato();
        if (candidato == null) {
            throw new ApiException("El servicio no tiene un candidato asociado", HttpStatus.NOT_FOUND);
        }

        HojaVidaEvaluado hv = hojaVidaRepository.findByCandidato_IdCandidato(candidato.getIdCandidato())
                .orElseThrow(() -> new ApiException(
                        "El candidato aún no ha diligenciado su hoja de vida", HttpStatus.NOT_FOUND));

        String ciudadNacimiento = nombreCiudad(candidato.getIdCiudadNacimiento());
        String ciudadResidencia = nombreCiudad(candidato.getIdCiudadResidencia());

        return new ResumenEvaluadoResponse(
                candidato.getNombres(), candidato.getApellidos(), candidato.getCedula(), candidato.getTipoDocumento(),
                hv.getCompletado(), hv.getFechaCompletado(), hv.getProgresoPorcentaje(), hv.getPasoActual(),
                hv.getFechaNacimiento(), hv.getEstadoCivil(), hv.getRh(), hv.getNivelEducativo(),
                ciudadNacimiento, ciudadResidencia,
                hv.getDireccion(), hv.getBarrio(), hv.getEstrato(),
                hv.getEmail(), hv.getCelular(), hv.getTelefonoFijo(),
                hv.getLibretaMilitar(), hv.getVisa(), hv.getPasaporte(), hv.getFondoPensiones(), hv.getEps(),
                hv.getEducacion().stream()
                        .map(e -> new ResumenEvaluadoResponse.EducacionItem(
                                e.getNivel(), e.getInstitucion(), e.getTitulo(),
                                e.getFechaInicio(), e.getFechaFin(), e.getEnCurso(), e.getCiudad()))
                        .toList(),
                hv.getExperienciaLaboral().stream()
                        .map(ex -> new ResumenEvaluadoResponse.ExperienciaItem(
                                ex.getEmpresa(), ex.getCargo(), ex.getFechaInicio(), ex.getFechaFin(),
                                ex.getLaboraActualmente(), ex.getCiudad(), ex.getTelefonoEmpresa(),
                                ex.getMotivoRetiro(), ex.getNombreJefe(), ex.getCargoJefe()))
                        .toList(),
                hv.getInactividades().stream()
                        .map(i -> new ResumenEvaluadoResponse.InactividadItem(
                                i.getFechaInicio(), i.getFechaFin(), i.getDiasInactivo(),
                                i.getJustificacion(), i.getRequiereCuestionario()))
                        .toList(),
                hv.getReferencias().stream()
                        .map(r -> new ResumenEvaluadoResponse.ReferenciaItem(
                                r.getNombre(), r.getParentesco(), r.getTelefono(), r.getTiempoConocimiento()))
                        .toList(),
                hv.getDocumentos().stream()
                        .map(d -> new ResumenEvaluadoResponse.DocumentoItem(
                                d.getId(), d.getTipoDocumento(), d.getNombreArchivo(), d.getFechaCarga()))
                        .toList()
        );
    }

    public record DescargaDocumento(Resource recurso, String nombreArchivo, String tipoContenido) {}

    @Transactional(readOnly = true)
    public DescargaDocumento descargarDocumento(Integer idServicio, Long idDocumento) {
        Servicio servicio = servicioRepository.findById(idServicio)
                .orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
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
    public List<LinkCandidatoResponse> listarHistorialPorServicio(Integer idServicio) {
        LocalDateTime ahora = LocalDateTime.now();
        Servicio servicio = servicioRepository.findById(idServicio).orElse(null);
        return linkRepository.findByServicio_IdServicioOrderByFechaCreacionDesc(idServicio).stream()
                .map(l -> toResponse(l, ahora, servicio))
                .toList();
    }

    @Transactional(readOnly = true)
    public LinkEstadisticasResponse estadisticas() {
        LocalDateTime ahora = LocalDateTime.now();
        long total      = linkRepository.count();
        long usados     = linkRepository.countByUsadoTrue();
        long expirados  = linkRepository.countByUsadoFalseAndFechaExpiracionBefore(ahora);
        long bloqueados = linkRepository.countByUsadoFalseAndIntentosFallidosGreaterThanEqual(MAX_INTENTOS);
        long pendientes = linkRepository
                .countByUsadoFalseAndFechaExpiracionAfterAndIntentosFallidosLessThan(ahora, MAX_INTENTOS);
        return new LinkEstadisticasResponse(total, pendientes, usados, expirados, bloqueados);
    }

    @Transactional
    public LinkCandidatoResponse generar(GenerarLinkRequest request, String emailActual, String ipOrigen) {
        Servicio servicio = servicioRepository.findById(request.idServicio())
                .orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));

        LocalDateTime ahora = LocalDateTime.now();

        // Validación 1: no puede existir ya un token activo para este servicio
        boolean yaExisteActivo = linkRepository
                .existsByServicio_IdServicioAndUsadoFalseAndFechaExpiracionAfterAndIntentosFallidosLessThan(
                        servicio.getIdServicio(), ahora, MAX_INTENTOS);
        if (yaExisteActivo) {
            throw new ApiException(
                    "Ya existe un link activo para el servicio #" + servicio.getIdServicio() +
                    ". Revócalo antes de generar uno nuevo.",
                    HttpStatus.CONFLICT);
        }

        // Validación 2: el mismo evaluado (cédula) no puede tener token activo en otro servicio
        if (servicio.getCandidato() != null) {
            String cedula = servicio.getCandidato().getCedula();
            List<Integer> otrosServiciosIds = servicioRepository
                    .findByCandidato_CedulaAndIdServicioNot(cedula, servicio.getIdServicio())
                    .stream()
                    .map(Servicio::getIdServicio)
                    .toList();

            if (!otrosServiciosIds.isEmpty()) {
                boolean cedulaConActivo = linkRepository.existsActivoEnServicios(
                        otrosServiciosIds, ahora, MAX_INTENTOS);
                if (cedulaConActivo) {
                    throw new ApiException(
                            "El evaluado con CC " + cedula +
                            " ya tiene un link activo en otro servicio. Revócalo primero.",
                            HttpStatus.CONFLICT);
                }
            }
        }

        Usuario generadoPor = usuarioRepository.findByEmail(emailActual).orElse(null);

        LinkCandidato link = LinkCandidato.builder()
                .servicio(servicio)
                .token(generarHex64())
                .fechaCreacion(ahora)
                .fechaExpiracion(ahora.plusHours(HORAS_EXPIRACION))
                .usado(false)
                .intentosFallidos((short) 0)
                .ipOrigen(ipOrigen)
                .generadoPor(generadoPor)
                .build();

        LinkCandidato guardado = linkRepository.save(link);
        log.info("Link generado — servicio={}, por={}", servicio.getIdServicio(), emailActual);
        return toResponse(guardado, ahora, servicio);
    }

    @Transactional(readOnly = true)
    public void enviarCorreo(Long id) {
        LinkCandidato link = linkRepository.findById(id)
                .orElseThrow(() -> new ApiException("Link no encontrado", HttpStatus.NOT_FOUND));

        LocalDateTime ahora = LocalDateTime.now();
        String estado = resolverEstado(link, ahora);
        if (!"PENDIENTE".equals(estado)) {
            throw new ApiException(
                    "Solo se puede enviar por correo un link en estado PENDIENTE (actual: " + estado + ")",
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
    public void revocar(Long id) {
        LinkCandidato link = linkRepository.findById(id)
                .orElseThrow(() -> new ApiException("Link no encontrado", HttpStatus.NOT_FOUND));
        if (Boolean.TRUE.equals(link.getUsado())) {
            throw new ApiException("El link ya fue utilizado o revocado", HttpStatus.CONFLICT);
        }
        link.setUsado(true);
        link.setFechaUso(LocalDateTime.now());
        log.info("Link {} revocado manualmente", id);
    }

    @Transactional
    public void eliminar(Long id) {
        LinkCandidato link = linkRepository.findById(id)
                .orElseThrow(() -> new ApiException("Link no encontrado", HttpStatus.NOT_FOUND));
        String estado = resolverEstado(link, LocalDateTime.now());
        if ("PENDIENTE".equals(estado)) {
            throw new ApiException(
                    "No puedes eliminar un link PENDIENTE. Revócalo primero.",
                    HttpStatus.CONFLICT);
        }
        linkRepository.delete(link);
        log.info("Link {} eliminado — estado previo: {}", id, estado);
    }

    @Transactional
    public LinkCandidatoResponse revertir(Long id) {
        LinkCandidato link = linkRepository.findById(id)
                .orElseThrow(() -> new ApiException("Link no encontrado", HttpStatus.NOT_FOUND));
        if (!Boolean.TRUE.equals(link.getUsado())) {
            throw new ApiException("Solo se puede revertir un link con estado USADO", HttpStatus.CONFLICT);
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (link.getFechaExpiracion() != null && link.getFechaExpiracion().isBefore(ahora)) {
            throw new ApiException(
                    "El link ya expiró. Genera uno nuevo para el evaluado.",
                    HttpStatus.GONE);
        }
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
        if (Boolean.TRUE.equals(l.getUsado())) return "USADO";
        if (l.getIntentosFallidos() != null && l.getIntentosFallidos() >= MAX_INTENTOS) return "BLOQUEADO";
        if (l.getFechaExpiracion() != null && l.getFechaExpiracion().isBefore(ahora)) return "EXPIRADO";
        return "PENDIENTE";
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
