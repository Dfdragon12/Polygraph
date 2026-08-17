package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.repository.TipoProgresoRepository;
import com.polygraph.erp.modules.gestor.dto.AsignacionResponse;
import com.polygraph.erp.modules.gestor.dto.AsignarMasivoRequest;
import com.polygraph.erp.modules.gestor.dto.AsignarRequest;
import com.polygraph.erp.modules.gestor.dto.EmpleadoAsignableResponse;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoSpecifications;
import com.polygraph.erp.modules.usuarios.entity.UsuariosInternos;
import com.polygraph.erp.modules.usuarios.repository.UsuariosIntenosRepository;
import com.polygraph.erp.shared.enums.EstadoAsignacion;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsignacionService {

    private final ServicioSubprocesoRepository asignacionRepository;
    private final UsuariosIntenosRepository    empleadoRepository;
    private final UsuarioRepository            usuarioRepository;
    private final TipoProgresoRepository       tipoProgresoRepository;

    @Transactional(readOnly = true)
    public Page<AsignacionResponse> listar(EstadoAsignacion estado, Integer idTipoProgreso,
                                            Long idUsuarioAsignado, LocalDateTime desde, LocalDateTime hasta,
                                            Integer idServicio, Long idGestor, Set<Rol> rolesPermitidos, Pageable pageable) {
        var spec = ServicioSubprocesoSpecifications.conFiltros(
                estado, idTipoProgreso, idUsuarioAsignado, desde, hasta, idServicio, idGestor, rolesPermitidos);
        return asignacionRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<AsignacionResponse> listarCalendario(LocalDateTime desde, LocalDateTime hasta, Long idGestor, Set<Rol> rolesPermitidos) {
        var spec = ServicioSubprocesoSpecifications.conFiltros(
                null, null, null, desde, hasta, null, idGestor, rolesPermitidos);
        return asignacionRepository.findAll(spec).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmpleadoAsignableResponse> empleadosDisponibles(Integer idTipoProgreso, Set<Rol> rolesPermitidos) {
        validarAlcance(findTipoProgreso(idTipoProgreso), rolesPermitidos);
        return empleadoRepository.findBySubprocesosAsignados_IdTipoProgresoAndActivoTrue(idTipoProgreso).stream()
                .map(emp -> usuarioRepository.findByIdEmpleado(emp.getIdEmpleado())
                        .map(u -> new EmpleadoAsignableResponse(u.getIdUsuario(), u.getNombre(), u.getApellido(), u.getRol().name()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Autoasignación: al crear un subproceso, si hay al menos un empleado activo calificado (con
     * ese subproceso marcado como capacidad), se le asigna automáticamente al que tenga MENOS
     * subprocesos ASIGNADOs vigentes ahora mismo — reparte la carga sin que el gestor/programador
     * tenga que elegir a mano en el caso normal. Si nadie tiene la capacidad todavía, el subproceso
     * queda PENDIENTE como antes, para asignación manual apenas se configure alguien.
     */
    @Transactional
    public void autoAsignar(ServicioSubproceso subproceso) {
        Integer idTipoProgreso = subproceso.getTipoProgreso().getIdTipoProgreso();
        List<UsuariosInternos> calificados = empleadoRepository
                .findBySubprocesosAsignados_IdTipoProgresoAndActivoTrue(idTipoProgreso);
        if (calificados.isEmpty()) return;

        UsuariosInternos elegido = calificados.stream()
                .min(Comparator.comparingLong(this::cargaActual))
                .orElse(null);
        Usuario usuario = elegido != null
                ? usuarioRepository.findByIdEmpleado(elegido.getIdEmpleado()).orElse(null)
                : null;
        if (usuario == null) return;

        subproceso.setUsuarioAsignado(usuario);
        subproceso.setEstado(EstadoAsignacion.ASIGNADO);
        subproceso.setFechaAsignacion(LocalDateTime.now());
        log.info("Autoasignado subproceso '{}' -> {} (menor carga entre {} calificados)",
                subproceso.getTipoProgreso().getNombreProgreso(), usuario.getEmail(), calificados.size());
    }

    private long cargaActual(UsuariosInternos emp) {
        return usuarioRepository.findByIdEmpleado(emp.getIdEmpleado())
                .map(u -> asignacionRepository.countByUsuarioAsignado_IdUsuarioAndEstado(u.getIdUsuario(), EstadoAsignacion.ASIGNADO))
                .orElse(0L);
    }

    @Transactional
    public AsignacionResponse asignar(Long id, AsignarRequest req, Set<Rol> rolesPermitidos) {
        ServicioSubproceso asignacion = asignacionRepository.findById(id)
                .orElseThrow(() -> new ApiException("Asignación no encontrada", HttpStatus.NOT_FOUND));
        validarAlcance(asignacion.getTipoProgreso(), rolesPermitidos);

        Usuario usuario = usuarioRepository.findById(req.idUsuarioAsignado())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        boolean tieneCapacidad = usuario.getIdEmpleado() != null
                && empleadoRepository.findBySubprocesosAsignados_IdTipoProgresoAndActivoTrue(
                        asignacion.getTipoProgreso().getIdTipoProgreso())
                .stream()
                .anyMatch(emp -> emp.getIdEmpleado().equals(usuario.getIdEmpleado()));

        if (!tieneCapacidad) {
            throw new ApiException(
                    "El usuario seleccionado no tiene el subproceso '" + asignacion.getTipoProgreso().getNombreProgreso() +
                    "' asignado como capacidad.", HttpStatus.BAD_REQUEST);
        }

        asignacion.setUsuarioAsignado(usuario);
        asignacion.setFechaProgramada(req.fechaProgramada());
        asignacion.setObservaciones(req.observaciones());
        asignacion.setEstado(EstadoAsignacion.ASIGNADO);
        asignacion.setFechaAsignacion(LocalDateTime.now());

        log.info("Asignación {} -> usuario {} (subproceso {})", id, usuario.getEmail(), asignacion.getTipoProgreso().getNombreProgreso());
        return toResponse(asignacion);
    }

    /** Asigna en bloque varios subprocesos del MISMO tipo de progreso a un solo empleado. */
    @Transactional
    public List<AsignacionResponse> asignarMasivo(AsignarMasivoRequest req, Long idGestor, Set<Rol> rolesPermitidos) {
        List<ServicioSubproceso> asignaciones = asignacionRepository.findAllById(req.ids());
        if (asignaciones.size() != req.ids().size()) {
            throw new ApiException("Alguno de los subprocesos seleccionados no existe.", HttpStatus.NOT_FOUND);
        }

        Integer idTipoProgreso = asignaciones.get(0).getTipoProgreso().getIdTipoProgreso();
        boolean mismoTipo = asignaciones.stream()
                .allMatch(a -> a.getTipoProgreso().getIdTipoProgreso().equals(idTipoProgreso));
        if (!mismoTipo) {
            throw new ApiException(
                    "La asignación masiva solo permite subprocesos del mismo tipo.", HttpStatus.BAD_REQUEST);
        }
        validarAlcance(asignaciones.get(0).getTipoProgreso(), rolesPermitidos);

        if (idGestor != null) {
            boolean fueraDeAlcance = asignaciones.stream().anyMatch(a -> {
                var cliente = a.getServicio() != null ? a.getServicio().getCliente() : null;
                return cliente == null || !idGestor.equals(cliente.getIdGestor());
            });
            if (fueraDeAlcance) {
                throw new ApiException("No tienes acceso a alguno de los subprocesos seleccionados.", HttpStatus.FORBIDDEN);
            }
        }

        Usuario usuario = usuarioRepository.findById(req.idUsuarioAsignado())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        boolean tieneCapacidad = usuario.getIdEmpleado() != null
                && empleadoRepository.findBySubprocesosAsignados_IdTipoProgresoAndActivoTrue(idTipoProgreso)
                .stream()
                .anyMatch(emp -> emp.getIdEmpleado().equals(usuario.getIdEmpleado()));
        if (!tieneCapacidad) {
            throw new ApiException(
                    "El usuario seleccionado no tiene el subproceso '" +
                    asignaciones.get(0).getTipoProgreso().getNombreProgreso() +
                    "' asignado como capacidad.", HttpStatus.BAD_REQUEST);
        }

        LocalDateTime ahora = LocalDateTime.now();
        for (ServicioSubproceso a : asignaciones) {
            a.setUsuarioAsignado(usuario);
            a.setFechaProgramada(req.fechaProgramada());
            a.setObservaciones(req.observaciones());
            a.setEstado(EstadoAsignacion.ASIGNADO);
            a.setFechaAsignacion(ahora);
        }

        log.info("Asignación masiva: {} subprocesos ({}) -> usuario {}",
                asignaciones.size(), asignaciones.get(0).getTipoProgreso().getNombreProgreso(), usuario.getEmail());

        return asignaciones.stream().map(this::toResponse).toList();
    }

    @Transactional
    public AsignacionResponse completar(Long id, Set<Rol> rolesPermitidos) {
        ServicioSubproceso asignacion = obtenerOFallar(id);
        validarAlcance(asignacion.getTipoProgreso(), rolesPermitidos);
        if (asignacion.getEstado() != EstadoAsignacion.ASIGNADO) {
            throw new ApiException("Solo se puede completar una asignación en estado ASIGNADO", HttpStatus.CONFLICT);
        }
        asignacion.setEstado(EstadoAsignacion.COMPLETADO);
        asignacion.setFechaCompletado(LocalDateTime.now());
        return toResponse(asignacion);
    }

    @Transactional
    public AsignacionResponse desasignar(Long id, Set<Rol> rolesPermitidos) {
        ServicioSubproceso asignacion = obtenerOFallar(id);
        validarAlcance(asignacion.getTipoProgreso(), rolesPermitidos);
        asignacion.setUsuarioAsignado(null);
        asignacion.setFechaProgramada(null);
        asignacion.setFechaAsignacion(null);
        asignacion.setEstado(EstadoAsignacion.PENDIENTE);
        return toResponse(asignacion);
    }

    @Transactional
    public AsignacionResponse cancelar(Long id, Set<Rol> rolesPermitidos) {
        ServicioSubproceso asignacion = obtenerOFallar(id);
        validarAlcance(asignacion.getTipoProgreso(), rolesPermitidos);
        asignacion.setEstado(EstadoAsignacion.CANCELADO);
        return toResponse(asignacion);
    }

    private ServicioSubproceso obtenerOFallar(Long id) {
        return asignacionRepository.findById(id)
                .orElseThrow(() -> new ApiException("Asignación no encontrada", HttpStatus.NOT_FOUND));
    }

    private TipoProgreso findTipoProgreso(Integer id) {
        return tipoProgresoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Tipo de progreso no encontrado", HttpStatus.NOT_FOUND));
    }

    /** PROGRAMADOR solo puede ver/actuar sobre subprocesos de poligrafía/visita; GESTOR no tiene restricción (rolesPermitidos = null). */
    private void validarAlcance(TipoProgreso tipo, Set<Rol> rolesPermitidos) {
        if (rolesPermitidos != null && !rolesPermitidos.contains(tipo.getRolResponsable())) {
            throw new ApiException(
                    "No tienes acceso al subproceso '" + tipo.getNombreProgreso() + "'.", HttpStatus.FORBIDDEN);
        }
    }

    private AsignacionResponse toResponse(ServicioSubproceso a) {
        var servicio   = a.getServicio();
        var candidato  = servicio != null ? servicio.getCandidato() : null;
        var usuario    = a.getUsuarioAsignado();
        String nombreAsignado = usuario != null
                ? usuario.getNombre() + (usuario.getApellido() != null ? " " + usuario.getApellido() : "")
                : null;

        return new AsignacionResponse(
                a.getId(),
                servicio != null ? servicio.getIdServicio() : null,
                candidato != null ? candidato.getNombres()   : null,
                candidato != null ? candidato.getApellidos() : null,
                candidato != null ? candidato.getCedula()    : null,
                servicio != null ? servicio.getCargo() : null,
                servicio != null && servicio.getProceso() != null ? servicio.getProceso().getNombreProceso() : null,
                a.getTipoProgreso().getIdTipoProgreso(),
                a.getTipoProgreso().getNombreProgreso(),
                a.getTipoProgreso().getMinutosEstimados(),
                a.getEstado().name(),
                usuario != null ? usuario.getIdUsuario() : null,
                nombreAsignado,
                a.getFechaProgramada(),
                a.getFechaAsignacion(),
                a.getFechaCompletado(),
                a.getObservaciones()
        );
    }
}
