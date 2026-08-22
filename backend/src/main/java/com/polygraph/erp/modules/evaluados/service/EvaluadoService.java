package com.polygraph.erp.modules.evaluados.service;

import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.evaluados.dto.*;
import com.polygraph.erp.modules.evaluados.entity.*;
import com.polygraph.erp.modules.evaluados.repository.CandidatoRepository;
import com.polygraph.erp.modules.evaluados.repository.DocumentoEvaluadoRepository;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.shared.entity.Notificacion;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final AlmacenamientoDocumentosService almacenamientoService;

    @Transactional
    public LinkValidacionResponse validarLink(String token) {
        LinkCandidato link = obtenerLinkValido(token);
        Servicio servicio = obtenerServicio(link);
        Candidato candidato = servicio.getCandidato();

        if (link.getFechaPrimerIngreso() == null) {
            link.setFechaPrimerIngreso(LocalDateTime.now());
            linkCandidatoRepository.save(link);
        }

        boolean completada = candidato != null && hojaVidaRepository
                .findByCandidato_IdCandidato(candidato.getIdCandidato())
                .map(HojaVidaEvaluado::getCompletado)
                .orElse(false);

        return new LinkValidacionResponse(
                servicio.getIdServicio(),
                candidato != null ? candidato.getCedula()     : null,
                candidato != null ? candidato.getNombres()    : null,
                candidato != null ? candidato.getApellidos() : null,
                servicio.getCargo(),
                servicio.getProceso() != null ? servicio.getProceso().getNombreProceso() : null,
                completada);
    }

    @Transactional(readOnly = true)
    public HojaVidaRequest obtenerFormulario(String token) {
        LinkCandidato link = obtenerLinkValido(token);
        Servicio servicio = obtenerServicio(link);
        Candidato candidato = servicio.getCandidato();

        Long idCandidato = candidato != null ? candidato.getIdCandidato() : null;

        Integer idCiudadNacimiento = candidato != null ? candidato.getIdCiudadNacimiento() : null;
        Integer idCiudadResidencia = candidato != null ? candidato.getIdCiudadResidencia() : null;

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
                        hv.getEmail(),
                        hv.getCelular(),
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
                                        e.getEnCurso(), e.getCiudad()))
                                .toList(),
                        hv.getExperienciaLaboral().stream()
                                .map(ex -> new ExperienciaLaboralRequest(ex.getEmpresa(), ex.getCargo(),
                                        ex.getFechaInicio(), ex.getFechaFin(), ex.getLaboraActualmente(),
                                        ex.getCiudad(), ex.getTelefonoEmpresa(), ex.getMotivoRetiro(),
                                        ex.getNombreJefe(), ex.getCargoJefe()))
                                .toList(),
                        hv.getReferencias().stream()
                                .map(r -> new ReferenciaPersonalRequest(r.getNombre(), r.getParentesco(),
                                        r.getTelefono(), r.getTiempoConocimiento()))
                                .toList(),
                        hv.getInactividades().stream()
                                .filter(i -> i.getJustificacion() != null)
                                .collect(java.util.stream.Collectors.toMap(
                                        i -> i.getFechaInicio() + "_" + i.getFechaFin(),
                                        InactividadLaboralEvaluado::getJustificacion,
                                        (a, b) -> a)),
                        hv.getPasoActual()))
                .orElse(new HojaVidaRequest(false, null, null,
                        idCiudadNacimiento, idCiudadResidencia,
                        null, null, null, null, null,
                        candidato != null ? candidato.getEmailPrincipal() : null,
                        candidato != null ? candidato.getCelular() : null,
                        null, null, null, null, null, null, null,
                        List.of(), List.of(), List.of(), Map.of(), null));
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

        Servicio servicio = obtenerServicio(link);
        guardar(servicio, request, true);

        link.setUsado(true);
        link.setFechaUso(LocalDateTime.now());
        linkCandidatoRepository.save(link);

        log.info("Hoja de vida completada: servicio={}, candidato={}",
                servicio.getIdServicio(),
                servicio.getCandidato() != null ? servicio.getCandidato().getCedula() : null);

        notificarFormularioCompletado(servicio);
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

        if (request.idCiudadNacimiento() != null || request.idCiudadResidencia() != null) {
            candidato.setIdCiudadNacimiento(request.idCiudadNacimiento());
            candidato.setIdCiudadResidencia(request.idCiudadResidencia());
            candidatoRepository.save(candidato);
        }

        if (request.pasoActual() != null) {
            hojaVida.setPasoActual(request.pasoActual());
        }
        hojaVida.setAutorizacionDatos(Boolean.TRUE.equals(request.autorizacionDatos()));
        if (Boolean.TRUE.equals(request.autorizacionDatos()) && hojaVida.getFechaAutorizacion() == null) {
            hojaVida.setFechaAutorizacion(LocalDateTime.now());
        }
        hojaVida.setFechaNacimiento(request.fechaNacimiento());
        hojaVida.setLugarNacimiento(request.lugarNacimiento());
        hojaVida.setEstadoCivil(request.estadoCivil());
        hojaVida.setNivelEducativo(request.nivelEducativo());
        hojaVida.setDireccion(request.direccion());
        hojaVida.setBarrio(request.barrio());
        hojaVida.setEstrato(request.estrato());
        hojaVida.setEmail(request.email());
        hojaVida.setCelular(request.celular());
        hojaVida.setRh(request.rh());
        hojaVida.setLibretaMilitar(request.libretaMilitar());
        hojaVida.setVisa(request.visa());
        hojaVida.setPasaporte(request.pasaporte());
        hojaVida.setFondoPensiones(request.fondoPensiones());
        hojaVida.setEps(request.eps());
        hojaVida.setTelefonoFijo(request.telefonoFijo());

        // Reemplazar educación
        hojaVida.getEducacion().clear();
        if (request.educacion() != null) {
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
                            .build()));
        }

        // Recalcular inactividades
        hojaVida.getInactividades().clear();
        hojaVidaRepository.save(hojaVida);

        List<InactividadLaboralEvaluado> inactividades = historialLaboralService
                .calcularInactividades(hojaVida, hojaVida.getExperienciaLaboral());
        Map<String, String> justificaciones = request.justificacionesInactividad() != null
                ? request.justificacionesInactividad() : Map.of();
        inactividades.forEach(inact -> inact.setJustificacion(
                justificaciones.get(inact.getFechaInicio() + "_" + inact.getFechaFin())));
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
        HojaVidaEvaluado hojaVida = obtenerOCrearHojaVida(candidato);

        // Reemplaza el documento existente del mismo tipo, si lo hay (un archivo activo por tipo)
        documentoRepository.findByHojaVidaEvaluado_IdAndTipoDocumento(hojaVida.getId(), tipoDocumento)
                .ifPresent(existente -> {
                    almacenamientoService.eliminar(existente.getRutaArchivo());
                    documentoRepository.delete(existente);
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

        almacenamientoService.eliminar(documento.getRutaArchivo());
        documentoRepository.delete(documento);
    }

    private LinkCandidato obtenerLinkValido(String token) {
        LinkCandidato link = linkCandidatoRepository.findByToken(token)
                .orElseThrow(() -> new ApiException("Link no encontrado o inválido", HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(link.getUsado())) {
            throw new ApiException("Este link ya fue utilizado", HttpStatus.GONE);
        }
        if (LocalDateTime.now().isAfter(link.getFechaExpiracion())) {
            throw new ApiException("El link ha expirado", HttpStatus.GONE);
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
