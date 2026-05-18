package com.polygraph.erp.modules.evaluados.service;

import com.polygraph.erp.modules.evaluados.dto.*;
import com.polygraph.erp.modules.evaluados.entity.*;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.modules.solicitudes.entity.Solicitud;
import com.polygraph.erp.modules.solicitudes.repository.SolicitudRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EvaluadoService {

    private final LinkCandidatoRepository linkCandidatoRepository;
    private final SolicitudRepository solicitudRepository;
    private final HojaVidaEvaluadoRepository hojaVidaRepository;
    private final HistorialLaboralService historialLaboralService;

    @Transactional(readOnly = true)
    public LinkValidacionResponse validarLink(String token) {
        LinkCandidato link = obtenerLinkValido(token);
        Solicitud solicitud = obtenerSolicitud(link);

        boolean completada = hojaVidaRepository
                .findBySolicitud_IdSolicitud(solicitud.getIdSolicitud())
                .map(HojaVidaEvaluado::getCompletado)
                .orElse(false);

        List<String> servicios = solicitud.getServicios().stream()
                .map(ss -> ss.getCatalogoServicio().getNombre())
                .toList();

        return new LinkValidacionResponse(
                solicitud.getIdSolicitud(),
                solicitud.getCedulaEvaluado(),
                solicitud.getNombresEvaluado(),
                solicitud.getApellidosEvaluado(),
                solicitud.getCargo(),
                servicios,
                completada);
    }

    @Transactional(readOnly = true)
    public HojaVidaRequest obtenerFormulario(String token) {
        LinkCandidato link = obtenerLinkValido(token);
        Solicitud solicitud = obtenerSolicitud(link);

        return hojaVidaRepository.findBySolicitud_IdSolicitud(solicitud.getIdSolicitud())
                .map(hv -> new HojaVidaRequest(
                        hv.getAutorizacionDatos(),
                        hv.getFechaNacimiento(),
                        hv.getLugarNacimiento(),
                        hv.getEstadoCivil(),
                        hv.getNivelEducativo(),
                        hv.getDireccion(),
                        hv.getBarrio(),
                        hv.getEstrato(),
                        hv.getEmail(),
                        hv.getCelular(),
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
                                .toList()))
                .orElse(new HojaVidaRequest(false, null, null, null, null, null,
                        null, null, solicitud.getEmailEvaluado(), solicitud.getCelularEvaluado(),
                        List.of(), List.of(), List.of()));
    }

    public void guardarProgreso(String token, HojaVidaRequest request) {
        LinkCandidato link = obtenerLinkValido(token);
        Solicitud solicitud = obtenerSolicitud(link);
        guardar(solicitud, request, false);
    }

    public void enviarFormulario(String token, HojaVidaRequest request) {
        LinkCandidato link = obtenerLinkValido(token);

        if (!Boolean.TRUE.equals(request.autorizacionDatos())) {
            throw new ApiException("Debe aceptar la autorización de manejo de datos", HttpStatus.BAD_REQUEST);
        }

        Solicitud solicitud = obtenerSolicitud(link);
        guardar(solicitud, request, true);

        link.setUsado(true);
        linkCandidatoRepository.save(link);

        log.info("Hoja de vida completada: solicitud={}, evaluado={}",
                solicitud.getIdSolicitud(), solicitud.getCedulaEvaluado());
    }

    private void guardar(Solicitud solicitud, HojaVidaRequest request, boolean marcarCompleto) {
        HojaVidaEvaluado hojaVida = hojaVidaRepository
                .findBySolicitud_IdSolicitud(solicitud.getIdSolicitud())
                .orElseGet(() -> HojaVidaEvaluado.builder()
                        .solicitud(solicitud)
                        .autorizacionDatos(false)
                        .completado(false)
                        .progresoPorcentaje(0)
                        .build());

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

    private Solicitud obtenerSolicitud(LinkCandidato link) {
        return solicitudRepository.findById(link.getIdSolicitud())
                .orElseThrow(() -> new ApiException("Solicitud asociada no encontrada", HttpStatus.NOT_FOUND));
    }
}
