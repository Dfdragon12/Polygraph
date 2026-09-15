package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.catalogo.repository.TipoProgresoRepository;
import com.polygraph.erp.modules.gestor.dto.AsignacionResponse;
import com.polygraph.erp.modules.gestor.dto.AsignarMasivoRequest;
import com.polygraph.erp.modules.gestor.dto.AsignarRequest;
import com.polygraph.erp.modules.gestor.dto.ConfirmarAsistenciaRequest;
import com.polygraph.erp.modules.gestor.dto.ConteoAsignacionesResponse;
import com.polygraph.erp.modules.gestor.dto.EmpleadoAsignableResponse;
import com.polygraph.erp.modules.gestor.dto.FranjaHorariaResponse;
import com.polygraph.erp.modules.gestor.dto.HistorialAsignacionResponse;
import com.polygraph.erp.modules.gestor.dto.HistorialLlamadaResponse;
import com.polygraph.erp.modules.gestor.dto.ProgramadorOpcionResponse;
import com.polygraph.erp.modules.gestor.dto.SubprocesoOpcionResponse;
import com.polygraph.erp.modules.formularios.entity.Formulario;
import com.polygraph.erp.modules.formularios.repository.FormularioDatoRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioFilaRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioSeccionRepository;
import com.polygraph.erp.modules.servicios.entity.HistorialEstadoServicio;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.HistorialEstadoServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoSpecifications;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.modules.usuarios.entity.EmpleadoCiudad;
import com.polygraph.erp.modules.usuarios.repository.EmpleadoCiudadRepository;
import com.polygraph.erp.modules.usuarios.repository.UsuariosIntenosRepository;
import com.polygraph.erp.shared.entity.Notificacion;
import com.polygraph.erp.shared.enums.AccionHistorialAsignacion;
import com.polygraph.erp.shared.enums.EstadoAsignacion;
import com.polygraph.erp.shared.enums.EstadoConfirmacionAsistencia;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsignacionService {

    /** Duración a asumir cuando el tipo de progreso no tiene minutos estimados configurados. */
    private static final int DURACION_DEFAULT_MINUTOS = 60;
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    /** Jornada laboral asumida para generar las franjas horarias sugeridas al asignar. */
    private static final LocalTime JORNADA_INICIO = LocalTime.of(8, 0);
    private static final LocalTime JORNADA_FIN = LocalTime.of(17, 0);

    private final ServicioSubprocesoRepository asignacionRepository;
    private final UsuariosIntenosRepository    empleadoRepository;
    private final UsuarioRepository            usuarioRepository;
    private final TipoProgresoRepository       tipoProgresoRepository;
    private final EmpleadoCiudadRepository     empleadoCiudadRepository;
    private final CiudadRepository             ciudadRepository;
    private final HistorialAsignacionService   historialAsignacionService;
    private final HistorialEstadoServicioRepository historialEstadoServicioRepository;
    private final HistorialLlamadaService historialLlamadaService;
    private final NotificacionRepository notificacionRepository;
    private final FormularioSeccionRepository formularioSeccionRepository;
    private final FormularioRepository formularioRepository;
    private final FormularioDatoRepository formularioDatoRepository;
    private final FormularioFilaRepository formularioFilaRepository;

    @Transactional(readOnly = true)
    public Page<AsignacionResponse> listar(EstadoAsignacion estado, Integer idTipoProgreso,
                                            Long idUsuarioAsignado, LocalDateTime desde, LocalDateTime hasta,
                                            Integer idServicio, Long idGestor, Set<Rol> rolesPermitidos, Pageable pageable,
                                            Long idProgramadorResponsable) {
        var spec = ServicioSubprocesoSpecifications.conFiltros(
                estado, idTipoProgreso, idUsuarioAsignado, desde, hasta, idServicio, idGestor, rolesPermitidos,
                idProgramadorResponsable);
        return asignacionRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<AsignacionResponse> listarCalendario(LocalDateTime desde, LocalDateTime hasta, Long idGestor,
                                                       Set<Rol> rolesPermitidos, Long idProgramadorResponsable) {
        var spec = ServicioSubprocesoSpecifications.conFiltros(
                null, null, null, desde, hasta, null, idGestor, rolesPermitidos, idProgramadorResponsable);
        return asignacionRepository.findAll(spec).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConteoAsignacionesResponse contarPorEstado(Long idGestor, Long idUsuarioAsignado, Set<Rol> rolesPermitidos,
                                                        Long idProgramadorResponsable) {
        long pendientes  = contarEstado(EstadoAsignacion.PENDIENTE, idGestor, idUsuarioAsignado, rolesPermitidos, idProgramadorResponsable);
        long asignados   = contarEstado(EstadoAsignacion.ASIGNADO, idGestor, idUsuarioAsignado, rolesPermitidos, idProgramadorResponsable);
        long completados = contarEstado(EstadoAsignacion.COMPLETADO, idGestor, idUsuarioAsignado, rolesPermitidos, idProgramadorResponsable);
        long cancelados  = contarEstado(EstadoAsignacion.CANCELADO, idGestor, idUsuarioAsignado, rolesPermitidos, idProgramadorResponsable);
        return new ConteoAsignacionesResponse(
                pendientes, asignados, completados, cancelados,
                pendientes + asignados + completados + cancelados);
    }

    private long contarEstado(EstadoAsignacion estado, Long idGestor, Long idUsuarioAsignado, Set<Rol> rolesPermitidos,
                               Long idProgramadorResponsable) {
        var spec = ServicioSubprocesoSpecifications.conFiltros(
                estado, null, idUsuarioAsignado, null, null, null, idGestor, rolesPermitidos, idProgramadorResponsable);
        return asignacionRepository.count(spec);
    }

    /** Opciones para el filtro "Subproceso" de las pantallas de Gestor/Programador — acotadas al
     *  alcance del rol (ANALISTA_INTERNO para GESTOR, POLIGRAFISTA/VISITADOR para PROGRAMADOR),
     *  no al catálogo completo (ese sigue siendo exclusivo de ADMIN_POLYGRAPH). */
    @Transactional(readOnly = true)
    public List<SubprocesoOpcionResponse> listarSubprocesosPermitidos(Set<Rol> rolesPermitidos) {
        List<TipoProgreso> tipos = rolesPermitidos != null
                ? tipoProgresoRepository.findByRolResponsableInAndActivoTrueOrderByOrdenAscNombreProgresoAsc(rolesPermitidos)
                : tipoProgresoRepository.findAllByOrderByOrdenAscNombreProgresoAsc();
        return tipos.stream()
                .map(t -> new SubprocesoOpcionResponse(t.getIdTipoProgreso(), t.getNombreProgreso()))
                .toList();
    }

    /** @param idCiudad ciudad del evaluado (residencia). Solo aplica a POLIGRAFISTA y VISITADOR:
     *  ANALISTA_INTERNO trabaja de forma virtual, así que la ciudad del evaluado no debe influir
     *  en quién aparece. Para POLIGRAFISTA/VISITADOR, si se indica la ciudad, solo se devuelven
     *  los empleados que la cubren en Equipo Polygraph — quien no la cubre no puede atenderla
     *  presencialmente, así que ni siquiera aparece como opción para seleccionar. */
    @Transactional(readOnly = true)
    public List<EmpleadoAsignableResponse> empleadosDisponibles(Integer idTipoProgreso, Integer idCiudad, Set<Rol> rolesPermitidos) {
        TipoProgreso tipo = findTipoProgreso(idTipoProgreso);
        validarAlcance(tipo, rolesPermitidos);
        boolean requiereCiudad = tipo.getRolResponsable() == Rol.POLIGRAFISTA || tipo.getRolResponsable() == Rol.VISITADOR;

        // Collectors.toMap no admite valores null y numeroSala SIEMPRE es null para VISITADOR
        // (solo cubre la ciudad, sin sala física) — de ahí el 500 al construir este mapa con un
        // collector. Se arma a mano para tolerar ese null.
        Map<Integer, Integer> salaPorEmpleado = new HashMap<>();
        if (requiereCiudad && idCiudad != null) {
            for (EmpleadoCiudad ec : empleadoCiudadRepository.findByCiudad_IdCiudad(idCiudad)) {
                salaPorEmpleado.putIfAbsent(ec.getEmpleado().getIdEmpleado(), ec.getNumeroSala());
            }
        }

        List<EmpleadoAsignableResponse> disponibles = empleadoRepository.findBySubprocesosAsignados_IdTipoProgresoAndActivoTrue(idTipoProgreso).stream()
                .map(emp -> usuarioRepository.findByIdEmpleado(emp.getIdEmpleado())
                        .map(u -> {
                            boolean cubreLaCiudad = salaPorEmpleado.containsKey(emp.getIdEmpleado());
                            return new EmpleadoAsignableResponse(
                                    u.getIdUsuario(), u.getNombre(), u.getApellido(), u.getRol().name(),
                                    cubreLaCiudad, cubreLaCiudad ? salaPorEmpleado.get(emp.getIdEmpleado()) : null);
                        })
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();

        if (requiereCiudad && idCiudad != null) {
            disponibles = disponibles.stream().filter(EmpleadoAsignableResponse::cubreLaCiudad).toList();
        }

        return disponibles.stream()
                .sorted(Comparator.comparing(EmpleadoAsignableResponse::cubreLaCiudad).reversed())
                .toList();
    }

    /** Parte el día (jornada 8am-5pm) en franjas y marca cuáles ya están ocupadas por otra
     *  asignación ASIGNADA de ese mismo empleado — para que se elija una hora libre en vez de
     *  adivinar y toparse con el choque de horario al guardar.
     *  @param idTipoProgreso si se indica, el tamaño de franja por defecto es el estimado de ese
     *  subproceso (validando que el rol tenga alcance sobre él); si es null, es una consulta
     *  general de disponibilidad del empleado (ej. la pestaña "Disponibilidad"), sin acotar a un
     *  subproceso puntual, y el tamaño por defecto es {@link #DURACION_DEFAULT_MINUTOS}.
     *  @param duracionMinutosOverride duración a usar en vez de la anterior — si el gestor/
     *  programador la ajustó en el formulario, o el tamaño de bloque elegido en "Disponibilidad".
     *  @param idExcluir si se está reprogramando una asignación existente, su propio id, para
     *  que su franja actual no aparezca como "ocupada por sí misma". */
    @Transactional(readOnly = true)
    public List<FranjaHorariaResponse> franjasDisponibles(Long idUsuarioAsignado, LocalDate fecha,
                                                            Integer idTipoProgreso, Integer duracionMinutosOverride,
                                                            Long idExcluir, Set<Rol> rolesPermitidos) {
        if (!usuarioRepository.existsById(idUsuarioAsignado)) {
            throw new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND);
        }

        int duracionBase = DURACION_DEFAULT_MINUTOS;
        if (idTipoProgreso != null) {
            TipoProgreso tipo = findTipoProgreso(idTipoProgreso);
            validarAlcance(tipo, rolesPermitidos);
            duracionBase = duracionCatalogo(tipo);
        }

        int duracion = duracionMinutosOverride != null ? duracionMinutosOverride : duracionBase;
        int totalJornada = (int) java.time.Duration.between(JORNADA_INICIO, JORNADA_FIN).toMinutes();
        // Si el subproceso dura más que la jornada completa (catálogo con un estimado poco
        // realista, o una tarea que de por sí ocupa el día entero) se ofrece un único bloque de
        // toda la jornada en vez de no ofrecer ninguno — antes esto dejaba el selector vacío
        // en silencio para cualquier subproceso con minutos_estimados > 540 (9 horas).
        int duracionFranja = Math.min(duracion, totalJornada);

        List<ServicioSubproceso> agendaDelDia = asignacionRepository
                .findByUsuarioAsignado_IdUsuarioAndEstado(idUsuarioAsignado, EstadoAsignacion.ASIGNADO).stream()
                .filter(a -> !a.getId().equals(idExcluir)
                        && a.getFechaProgramada() != null
                        && a.getFechaProgramada().toLocalDate().equals(fecha))
                .toList();

        LocalDateTime inicioJornada = LocalDateTime.of(fecha, JORNADA_INICIO);
        LocalDateTime finJornada = LocalDateTime.of(fecha, JORNADA_FIN);

        // Candidatos de inicio de franja: la grilla fija de siempre (cada duracionFranja desde el
        // inicio de jornada) MÁS el fin real de cada bloque ocupado del día. Sin esto, una cita
        // corta que no cae justo en un múltiplo de duracionFranja (ej. termina 8:15 con franjas de
        // 30 min) dejaba ese resto de tiempo libre invisible — nunca aparecía como franja
        // disponible, aunque sí estuviera libre. Con TreeSet queda ordenado y sin duplicados.
        java.util.TreeSet<LocalDateTime> inicios = new java.util.TreeSet<>();
        for (LocalDateTime c = inicioJornada; !c.isAfter(finJornada); c = c.plusMinutes(duracionFranja)) {
            inicios.add(c);
        }
        for (ServicioSubproceso a : agendaDelDia) {
            LocalDateTime finBloque = finBloque(a);
            if (!finBloque.isBefore(inicioJornada) && !finBloque.isAfter(finJornada)) {
                inicios.add(finBloque);
            }
        }

        List<FranjaHorariaResponse> franjas = new ArrayList<>();
        for (LocalDateTime inicioFranja : inicios) {
            LocalDateTime finFranja = inicioFranja.plusMinutes(duracionFranja);
            if (finFranja.isAfter(finJornada)) continue; // no cabe completa dentro de la jornada

            ServicioSubproceso choque = agendaDelDia.stream()
                    .filter(a -> inicioFranja.isBefore(finBloque(a)) && a.getFechaProgramada().isBefore(finFranja))
                    .findFirst().orElse(null);

            franjas.add(new FranjaHorariaResponse(
                    inicioFranja, finFranja, choque == null,
                    choque != null
                            ? choque.getTipoProgreso().getNombreProgreso()
                                    + (choque.getServicio() != null ? " (servicio #" + choque.getServicio().getIdServicio() + ")" : "")
                            : null));
        }
        return franjas;
    }

    private LocalDateTime finBloque(ServicioSubproceso a) {
        return a.getFechaProgramada().plusMinutes(duracionEfectiva(a));
    }

    /** Duración a usar para un tipo de progreso cuando no se ajustó una para el caso puntual. */
    private int duracionCatalogo(TipoProgreso tipo) {
        return tipo.getMinutosEstimados() != null ? tipo.getMinutosEstimados() : DURACION_DEFAULT_MINUTOS;
    }

    /** Duración real de este subproceso: la que le ajustó gestor/programador al asignarlo si la
     *  hay, si no la del catálogo — el tiempo real varía caso a caso. */
    private int duracionEfectiva(ServicioSubproceso a) {
        return a.getDuracionMinutos() != null ? a.getDuracionMinutos() : duracionCatalogo(a.getTipoProgreso());
    }

    /** El ajuste de duración es para AUMENTAR el tiempo frente al estimado del catálogo, nunca
     *  para reducirlo por debajo de ese mínimo. */
    private void validarDuracionMinima(Integer duracionSolicitada, TipoProgreso tipo) {
        if (duracionSolicitada == null) return;
        int minimo = duracionCatalogo(tipo);
        if (duracionSolicitada < minimo) {
            throw new ApiException(
                    "La duración no puede ser menor al estimado del catálogo para '" + tipo.getNombreProgreso() +
                    "' (" + minimo + " min).", HttpStatus.BAD_REQUEST);
        }
    }

    /** No tiene sentido programar una asignación en una fecha/hora que ya pasó — el frontend ya
     *  lo evita (no ofrece días/horas anteriores a ahora), esto es el respaldo del backend. */
    private void validarNoEnElPasado(LocalDateTime fechaProgramada) {
        if (fechaProgramada != null && fechaProgramada.isBefore(LocalDateTime.now())) {
            throw new ApiException("No se puede programar una fecha/hora que ya pasó.", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public AsignacionResponse asignar(Long id, AsignarRequest req, Usuario actor, Set<Rol> rolesPermitidos) {
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

        validarDuracionMinima(req.duracionMinutos(), asignacion.getTipoProgreso());
        validarNoEnElPasado(req.fechaProgramada());
        int duracionNueva = req.duracionMinutos() != null ? req.duracionMinutos() : duracionCatalogo(asignacion.getTipoProgreso());
        validarSinChoqueDeHorario(usuario, duracionNueva, req.fechaProgramada(), Set.of(id));

        Usuario usuarioAnterior = asignacion.getUsuarioAsignado();
        LocalDateTime fechaAnterior = asignacion.getFechaProgramada();

        asignacion.setUsuarioAsignado(usuario);
        asignacion.setFechaProgramada(req.fechaProgramada());
        asignacion.setObservaciones(req.observaciones());
        asignacion.setEstado(EstadoAsignacion.ASIGNADO);
        asignacion.setFechaAsignacion(LocalDateTime.now());
        asignacion.setDuracionMinutos(req.duracionMinutos());

        historialAsignacionService.registrar(asignacion,
                usuarioAnterior == null ? AccionHistorialAsignacion.ASIGNADO : AccionHistorialAsignacion.REASIGNADO,
                usuarioAnterior, usuario, fechaAnterior, req.fechaProgramada(), actor, req.observaciones());
        avanzarAEnEjecucionSiCorresponde(asignacion.getServicio(), actor);

        log.info("Asignación {} -> usuario {} (subproceso {})", id, usuario.getEmail(), asignacion.getTipoProgreso().getNombreProgreso());
        return toResponse(asignacion);
    }

    /** Asigna en bloque varios subprocesos del MISMO tipo de progreso a un solo empleado. */
    @Transactional
    public List<AsignacionResponse> asignarMasivo(AsignarMasivoRequest req, Usuario actor, Long idGestor, Set<Rol> rolesPermitidos) {
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

        validarDuracionMinima(req.duracionMinutos(), asignaciones.get(0).getTipoProgreso());
        validarNoEnElPasado(req.fechaProgramada());
        Set<Long> idsDelLote = new HashSet<>(req.ids());
        int duracionNueva = req.duracionMinutos() != null ? req.duracionMinutos() : duracionCatalogo(asignaciones.get(0).getTipoProgreso());
        validarSinChoqueDeHorario(usuario, duracionNueva, req.fechaProgramada(), idsDelLote);

        LocalDateTime ahora = LocalDateTime.now();
        for (ServicioSubproceso a : asignaciones) {
            Usuario usuarioAnterior = a.getUsuarioAsignado();
            LocalDateTime fechaAnterior = a.getFechaProgramada();

            a.setUsuarioAsignado(usuario);
            a.setFechaProgramada(req.fechaProgramada());
            a.setObservaciones(req.observaciones());
            a.setEstado(EstadoAsignacion.ASIGNADO);
            a.setFechaAsignacion(ahora);
            a.setDuracionMinutos(req.duracionMinutos());

            historialAsignacionService.registrar(a,
                    usuarioAnterior == null ? AccionHistorialAsignacion.ASIGNADO : AccionHistorialAsignacion.REASIGNADO,
                    usuarioAnterior, usuario, fechaAnterior, req.fechaProgramada(), actor, req.observaciones());
            avanzarAEnEjecucionSiCorresponde(a.getServicio(), actor);
        }

        log.info("Asignación masiva: {} subprocesos ({}) -> usuario {}",
                asignaciones.size(), asignaciones.get(0).getTipoProgreso().getNombreProgreso(), usuario.getEmail());

        return asignaciones.stream().map(this::toResponse).toList();
    }

    /** Apenas un subproceso queda con alguien asignado, el servicio ya está "en marcha" — pasa
     *  automáticamente de PENDIENTE/PROGRAMANDO a EN_EJECUCION, sin esperar a que alguien lo
     *  cambie a mano. No toca servicios que ya avanzaron más (EN_EJECUCION, FINALIZADO, etc.) ni
     *  los cancelados/reprogramados. Se atribuye a quien hizo la asignación (mismo mecanismo que
     *  SolicitudService.cambiarEstado). */
    private void avanzarAEnEjecucionSiCorresponde(Servicio servicio, Usuario actor) {
        if (servicio == null) return;
        EstadoServicio estadoAnterior = servicio.getEstado();
        if (estadoAnterior != EstadoServicio.PENDIENTE && estadoAnterior != EstadoServicio.PROGRAMANDO) return;

        servicio.setEstado(EstadoServicio.EN_EJECUCION);

        historialEstadoServicioRepository.save(HistorialEstadoServicio.builder()
                .servicio(servicio)
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(EstadoServicio.EN_EJECUCION)
                .usuario(actor)
                .fechaCambio(LocalDateTime.now())
                .observacion("Cambio automático: un subproceso quedó asignado.")
                .build());

        log.info("Servicio {} pasó automáticamente de {} a EN_EJECUCION (subproceso asignado)",
                servicio.getIdServicio(), estadoAnterior);
    }

    @Transactional
    public AsignacionResponse completar(Long id, Usuario actor, Set<Rol> rolesPermitidos) {
        ServicioSubproceso asignacion = obtenerOFallar(id);
        validarAlcance(asignacion.getTipoProgreso(), rolesPermitidos);
        if (asignacion.getEstado() != EstadoAsignacion.ASIGNADO) {
            throw new ApiException("Solo se puede completar una asignación en estado ASIGNADO", HttpStatus.CONFLICT);
        }
        validarFormularioDiligenciado(asignacion);
        asignacion.setEstado(EstadoAsignacion.COMPLETADO);
        asignacion.setFechaCompletado(LocalDateTime.now());

        historialAsignacionService.registrar(asignacion, AccionHistorialAsignacion.COMPLETADO,
                asignacion.getUsuarioAsignado(), asignacion.getUsuarioAsignado(),
                asignacion.getFechaProgramada(), asignacion.getFechaProgramada(), actor, null);

        return toResponse(asignacion);
    }

    /** Si este tipo de progreso tiene una plantilla configurada (admin/catálogo → Plantilla),
     *  exige que ya se haya guardado al menos un dato antes de dejar completar — evitaba antes
     *  que un subproceso quedara COMPLETADO sin ningún Formulario, lo que después parece un
     *  bug ("ya está completado pero no hay nada que ver ni descargar") cuando en realidad nunca
     *  se guardó nada. Tipos de progreso sin plantilla configurada (todavía no se les armó una
     *  desde el constructor) no se ven afectados — para esos, completar sigue funcionando igual
     *  que siempre. */
    private void validarFormularioDiligenciado(ServicioSubproceso asignacion) {
        boolean tienePlantilla = formularioSeccionRepository
                .existsByTipoProgreso_IdTipoProgreso(asignacion.getTipoProgreso().getIdTipoProgreso());
        if (!tienePlantilla) return;

        Integer idFormulario = formularioRepository.findByServicioSubproceso_Id(asignacion.getId())
                .map(Formulario::getIdFormulario).orElse(null);
        boolean tieneDatos = idFormulario != null
                && (formularioDatoRepository.existsByFormulario_IdFormulario(idFormulario)
                        || formularioFilaRepository.existsByFormulario_IdFormulario(idFormulario));
        if (!tieneDatos) {
            throw new ApiException("Este servicio tiene un formulario configurado y todavía no se ha guardado "
                    + "ningún dato — guárdalo antes de marcarlo como completado.", HttpStatus.CONFLICT);
        }
    }

    @Transactional
    public AsignacionResponse desasignar(Long id, Usuario actor, Set<Rol> rolesPermitidos) {
        ServicioSubproceso asignacion = obtenerOFallar(id);
        validarAlcance(asignacion.getTipoProgreso(), rolesPermitidos);

        Usuario usuarioAnterior = asignacion.getUsuarioAsignado();
        LocalDateTime fechaAnterior = asignacion.getFechaProgramada();

        asignacion.setUsuarioAsignado(null);
        asignacion.setFechaProgramada(null);
        asignacion.setFechaAsignacion(null);
        asignacion.setEstado(EstadoAsignacion.PENDIENTE);

        historialAsignacionService.registrar(asignacion, AccionHistorialAsignacion.DESASIGNADO,
                usuarioAnterior, null, fechaAnterior, null, actor, null);

        return toResponse(asignacion);
    }

    @Transactional
    public AsignacionResponse cancelar(Long id, Usuario actor, Set<Rol> rolesPermitidos) {
        ServicioSubproceso asignacion = obtenerOFallar(id);
        validarAlcance(asignacion.getTipoProgreso(), rolesPermitidos);
        asignacion.setEstado(EstadoAsignacion.CANCELADO);

        historialAsignacionService.registrar(asignacion, AccionHistorialAsignacion.CANCELADO,
                asignacion.getUsuarioAsignado(), asignacion.getUsuarioAsignado(),
                asignacion.getFechaProgramada(), asignacion.getFechaProgramada(), actor, null);

        return toResponse(asignacion);
    }

    /** Registra el resultado de la llamada de confirmación de asistencia (poligrafía/visita) —
     *  sin auditoría propia, se sobrescribe con cada llamada nueva. A propósito NO exige que ya
     *  haya alguien/hora asignada: la idea es llamar primero para confirmar que el candidato va
     *  a estar, y con base en eso decidir a quién y cuándo asignar — no al revés. */
    @Transactional
    public AsignacionResponse confirmarAsistencia(Long id, ConfirmarAsistenciaRequest req, Usuario actor, Set<Rol> rolesPermitidos) {
        ServicioSubproceso asignacion = obtenerOFallar(id);
        validarAlcance(asignacion.getTipoProgreso(), rolesPermitidos);
        if (asignacion.getEstado() == EstadoAsignacion.CANCELADO) {
            throw new ApiException("No se puede registrar la confirmación de un subproceso cancelado.", HttpStatus.CONFLICT);
        }

        EstadoConfirmacionAsistencia estado;
        try {
            estado = EstadoConfirmacionAsistencia.valueOf(req.estadoConfirmacion());
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Estado de confirmación no válido.", HttpStatus.BAD_REQUEST);
        }

        asignacion.setEstadoConfirmacion(estado);
        asignacion.setNotasConfirmacion(req.notas());
        historialLlamadaService.registrar(asignacion, estado, req.notas(), actor);

        log.info("Confirmación de asistencia {} -> {} (subproceso {})", id, estado, asignacion.getTipoProgreso().getNombreProgreso());
        return toResponse(asignacion);
    }

    @Transactional(readOnly = true)
    public List<HistorialLlamadaResponse> historialLlamadas(Long id, Set<Rol> rolesPermitidos) {
        ServicioSubproceso asignacion = obtenerOFallar(id);
        validarAlcance(asignacion.getTipoProgreso(), rolesPermitidos);
        return historialLlamadaService.listar(id);
    }

    @Transactional(readOnly = true)
    public List<HistorialAsignacionResponse> historial(Long id, Set<Rol> rolesPermitidos) {
        ServicioSubproceso asignacion = obtenerOFallar(id);
        validarAlcance(asignacion.getTipoProgreso(), rolesPermitidos);
        return historialAsignacionService.listar(id);
    }

    /** El gestor ve los subprocesos de poligrafía/visita de sus clientes en solo lectura (los
     *  asigna PROGRAMADOR) — esto le deja ELEGIR a qué PROGRAMADOR encargarle coordinarlo, en vez
     *  de notificar a todos por igual. El programador elegido sigue siendo quien decide el
     *  poligrafista/visitador puntual y la fecha desde su propio módulo — esto solo dice quién
     *  quedó a cargo, y le manda la notificación solo a esa persona.
     *  A propósito NO usa validarAlcance (eso exigiría rolResponsable=ANALISTA_INTERNO para
     *  GESTOR): esta acción es justo para lo contrario, encargar poligrafía/visita. */
    @Transactional
    public AsignacionResponse asignarProgramador(Long id, Long idProgramador, Usuario actor) {
        ServicioSubproceso subproceso = obtenerOFallar(id);
        TipoProgreso tipo = subproceso.getTipoProgreso();
        Rol rolResponsable = tipo.getRolResponsable();
        if (rolResponsable != Rol.POLIGRAFISTA && rolResponsable != Rol.VISITADOR) {
            throw new ApiException("Este subproceso no requiere un programador.", HttpStatus.BAD_REQUEST);
        }

        Servicio servicio = subproceso.getServicio();
        if (actor.getRol() == Rol.GESTOR) {
            Long idGestorDelServicio = servicio != null && servicio.getCliente() != null
                    ? servicio.getCliente().getIdGestor() : null;
            if (!actor.getIdUsuario().equals(idGestorDelServicio)) {
                throw new ApiException("No tienes acceso a este servicio.", HttpStatus.FORBIDDEN);
            }
        }

        Usuario programador = usuarioRepository.findById(idProgramador)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (programador.getRol() != Rol.PROGRAMADOR || !Boolean.TRUE.equals(programador.getActivo())) {
            throw new ApiException(programador.getNombre() + " no es un programador activo.", HttpStatus.BAD_REQUEST);
        }

        subproceso.setProgramadorResponsable(programador);

        var candidato = servicio != null ? servicio.getCandidato() : null;
        String nombreEvaluado = candidato != null
                ? (candidato.getNombres() + " " + (candidato.getApellidos() != null ? candidato.getApellidos() : "")).trim()
                : "el evaluado";
        String nombreActor = actor.getNombre() != null ? actor.getNombre() : "Un gestor";
        Integer idServicio = servicio != null ? servicio.getIdServicio() : null;

        notificacionRepository.save(Notificacion.builder()
                .usuario(programador)
                .tipo("PROGRAMAR_ASIGNACION")
                .titulo(tipo.getNombreProgreso() + " por programar")
                .mensaje(nombreActor + " te encargó programar " + tipo.getNombreProgreso()
                        + " para " + nombreEvaluado + ".")
                .leida(false)
                .fechaCreacion(LocalDateTime.now())
                .referenciaTipo("SERVICIO")
                .referenciaId(idServicio != null ? idServicio.longValue() : null)
                .build());

        log.info("{} le encargó '{}' a {} (subproceso {})", actor.getEmail(), tipo.getNombreProgreso(), programador.getEmail(), id);
        return toResponse(subproceso);
    }

    /** Opciones para el selector "Asignar programador" — solo PROGRAMADOR activos. */
    @Transactional(readOnly = true)
    public List<ProgramadorOpcionResponse> listarProgramadoresActivos() {
        return usuarioRepository.findByRolIn(List.of(Rol.PROGRAMADOR)).stream()
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .map(u -> new ProgramadorOpcionResponse(u.getIdUsuario(), u.getNombre(), u.getApellido()))
                .sorted(Comparator.comparing(ProgramadorOpcionResponse::nombre))
                .toList();
    }

    private ServicioSubproceso obtenerOFallar(Long id) {
        return asignacionRepository.findById(id)
                .orElseThrow(() -> new ApiException("Asignación no encontrada", HttpStatus.NOT_FOUND));
    }

    private TipoProgreso findTipoProgreso(Integer id) {
        return tipoProgresoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Tipo de progreso no encontrado", HttpStatus.NOT_FOUND));
    }

    /** Evita que un mismo empleado quede con dos subprocesos programados en horarios que se cruzan.
     *  @param duracionNueva minutos que va a durar la nueva asignación (ajustada por el gestor/
     *  programador o el estimado del catálogo). Solo se compara contra subprocesos en estado
     *  ASIGNADO (los completados/cancelados ya no ocupan agenda), usando la duración real de cada
     *  uno (ajustada si la tiene, si no la del catálogo). */
    private void validarSinChoqueDeHorario(Usuario usuario, int duracionNueva, LocalDateTime fechaProgramada, Set<Long> idsExcluir) {
        if (fechaProgramada == null) return;

        LocalDateTime inicioNuevo = fechaProgramada;
        LocalDateTime finNuevo = fechaProgramada.plusMinutes(duracionNueva);

        List<ServicioSubproceso> agenda = asignacionRepository
                .findByUsuarioAsignado_IdUsuarioAndEstado(usuario.getIdUsuario(), EstadoAsignacion.ASIGNADO);

        for (ServicioSubproceso otra : agenda) {
            if (idsExcluir.contains(otra.getId()) || otra.getFechaProgramada() == null) continue;

            LocalDateTime inicioOtra = otra.getFechaProgramada();
            LocalDateTime finOtra = inicioOtra.plusMinutes(duracionEfectiva(otra));

            boolean seCruzan = inicioNuevo.isBefore(finOtra) && inicioOtra.isBefore(finNuevo);
            if (seCruzan) {
                Integer idServicioOtra = otra.getServicio() != null ? otra.getServicio().getIdServicio() : null;
                throw new ApiException(
                        usuario.getNombre() + " ya tiene '" + otra.getTipoProgreso().getNombreProgreso() +
                        "' agendado de " + inicioOtra.format(FORMATO_HORA) + " a " + finOtra.format(FORMATO_HORA) +
                        (idServicioOtra != null ? " (servicio #" + idServicioOtra + ")" : "") +
                        ", que se cruza con este horario.", HttpStatus.CONFLICT);
            }
        }
    }

    /** PROGRAMADOR solo puede ver/actuar sobre subprocesos de poligrafía/visita; GESTOR solo sobre
     *  subprocesos de ANALISTA_INTERNO (no asigna poligrafía ni visita — eso es de PROGRAMADOR). */
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

        // La ciudad del evaluado solo importa para POLIGRAFISTA/VISITADOR (trabajo presencial).
        // ANALISTA_INTERNO es virtual: no se informa ciudad ni se usa para priorizar empleados.
        Rol rolResponsable = a.getTipoProgreso().getRolResponsable();
        boolean requiereCiudad = rolResponsable == Rol.POLIGRAFISTA || rolResponsable == Rol.VISITADOR;
        Integer idCiudad = requiereCiudad && candidato != null ? candidato.getIdCiudadResidencia() : null;
        String nombreCiudad = idCiudad != null
                ? ciudadRepository.findById(idCiudad).map(Ciudad::getNombreCiudad).orElse(null)
                : null;

        Usuario programador = a.getProgramadorResponsable();
        String nombreProgramador = programador != null
                ? programador.getNombre() + (programador.getApellido() != null ? " " + programador.getApellido() : "")
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
                Boolean.TRUE.equals(a.getTipoProgreso().getRequiereDocumento()),
                rolResponsable.name(),
                duracionEfectiva(a),
                a.getEstado().name(),
                usuario != null ? usuario.getIdUsuario() : null,
                nombreAsignado,
                a.getFechaProgramada(),
                a.getFechaAsignacion(),
                a.getFechaCompletado(),
                servicio != null ? servicio.getFechaEntregaEstimada() : null,
                a.getObservaciones(),
                idCiudad,
                nombreCiudad,
                a.getEstadoConfirmacion() != null ? a.getEstadoConfirmacion().name() : null,
                a.getNotasConfirmacion(),
                programador != null ? programador.getIdUsuario() : null,
                nombreProgramador,
                a.getVersionPlantilla(),
                a.getTipoProgreso().getVersionPlantilla()
        );
    }
}
