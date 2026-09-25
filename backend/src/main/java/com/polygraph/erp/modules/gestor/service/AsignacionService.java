package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.catalogo.repository.TipoProgresoRepository;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.gestor.dto.AsignacionResponse;
import com.polygraph.erp.modules.gestor.dto.AsignarMasivoRequest;
import com.polygraph.erp.modules.gestor.dto.AsignarRequest;
import com.polygraph.erp.modules.gestor.dto.ConfirmarAsistenciaRequest;
import com.polygraph.erp.modules.gestor.dto.ConteoAsignacionesResponse;
import com.polygraph.erp.modules.gestor.dto.DevolverSubprocesoRequest;
import com.polygraph.erp.modules.gestor.dto.EmpleadoAsignableResponse;
import com.polygraph.erp.modules.gestor.dto.FranjaHorariaResponse;
import com.polygraph.erp.modules.gestor.dto.HistorialAsignacionResponse;
import com.polygraph.erp.modules.gestor.dto.HistorialLlamadaResponse;
import com.polygraph.erp.modules.gestor.dto.ProgramadorOpcionResponse;
import com.polygraph.erp.modules.gestor.dto.SubprocesoOpcionResponse;
import com.polygraph.erp.modules.formularios.entity.Formulario;
import com.polygraph.erp.modules.formularios.repository.FormularioDatoRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioFilaRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioPlantillaConfigRepository;
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
    private final FormularioPlantillaConfigRepository formularioPlantillaConfigRepository;
    private final HojaVidaEvaluadoRepository hojaVidaEvaluadoRepository;
    private final com.polygraph.erp.modules.formularios.service.FormularioService formularioService;
    private final org.springframework.context.ApplicationEventPublisher eventos;

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
        long publicados  = contarEstado(EstadoAsignacion.PUBLICADO, idGestor, idUsuarioAsignado, rolesPermitidos, idProgramadorResponsable);
        long cancelados  = contarEstado(EstadoAsignacion.CANCELADO, idGestor, idUsuarioAsignado, rolesPermitidos, idProgramadorResponsable);
        return new ConteoAsignacionesResponse(
                pendientes, asignados, completados, publicados, cancelados,
                pendientes + asignados + completados + publicados + cancelados);
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

    /** El candidato debe terminar de diligenciar su hoja de vida (link temporal) ANTES de que se
     *  pueda asignar cualquiera de sus subprocesos a un ANALISTA_INTERNO, POLIGRAFISTA/VISITADOR,
     *  o encargárselo a un PROGRAMADOR — evita repartir trabajo sobre datos que todavía no existen
     *  (el gestor podía asignar sin que el candidato hubiera diligenciado nada). Si el servicio no
     *  tiene candidato asociado (otros tipos de servicio sin ese flujo) no aplica restricción. */
    private void validarCandidatoCompleto(Servicio servicio) {
        if (servicio == null || servicio.getCandidato() == null) return;
        boolean completado = hojaVidaEvaluadoRepository
                .findByCandidato_IdCandidato(servicio.getCandidato().getIdCandidato())
                .map(HojaVidaEvaluado::getCompletado)
                .orElse(false);
        if (!Boolean.TRUE.equals(completado)) {
            throw new ApiException(
                    "El candidato todavía no ha diligenciado su información (link pendiente) — no se puede asignar hasta que termine.",
                    HttpStatus.CONFLICT);
        }
    }

    @Transactional
    public AsignacionResponse asignar(Long id, AsignarRequest req, Usuario actor, Set<Rol> rolesPermitidos) {
        ServicioSubproceso asignacion = asignacionRepository.findById(id)
                .orElseThrow(() -> new ApiException("Asignación no encontrada", HttpStatus.NOT_FOUND));
        validarAlcance(asignacion.getTipoProgreso(), rolesPermitidos);
        com.polygraph.erp.shared.utils.AccesoGestor.validar(asignacion, actor);
        validarCandidatoCompleto(asignacion.getServicio());

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
        recalcularEstadoServicio(asignacion.getServicio(), actor, "Subproceso asignado.");
        notificarSubprocesoAsignado(asignacion);

        log.info("Asignación {} -> usuario {} (subproceso {})", id, usuario.getEmail(), asignacion.getTipoProgreso().getNombreProgreso());
        return toResponse(asignacion);
    }

    /** Antes solo se notificaba al devolver/aprobar un subproceso — quien recibía trabajo nuevo
     *  no tenía ninguna señal salvo entrar manualmente a "Mis Asignaciones". Se reutiliza en
     *  asignar() y asignarMasivo(). */
    private void notificarSubprocesoAsignado(ServicioSubproceso asignacion) {
        Usuario usuario = asignacion.getUsuarioAsignado();
        if (usuario == null) return;
        String cuando = asignacion.getFechaProgramada() != null
                ? " para el " + asignacion.getFechaProgramada().format(FORMATO_HORA)
                : "";
        notificacionRepository.save(Notificacion.builder()
                .usuario(usuario)
                .tipo("SUBPROCESO_ASIGNADO")
                .titulo(asignacion.getTipoProgreso().getNombreProgreso() + " asignado")
                .mensaje("Te asignaron \"" + asignacion.getTipoProgreso().getNombreProgreso() + "\"" + cuando + ".")
                .leida(false)
                .fechaCreacion(LocalDateTime.now())
                .referenciaTipo("SERVICIO_SUBPROCESO")
                .referenciaId(asignacion.getId())
                .build());
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

        // Se valida ANTES de mutar nada (todo o nada): un lote puede mezclar subprocesos de
        // distintos candidatos/servicios, así que se revisa cada uno.
        asignaciones.forEach(a -> validarCandidatoCompleto(a.getServicio()));

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
            recalcularEstadoServicio(a.getServicio(), actor, "Subproceso asignado.");
            notificarSubprocesoAsignado(a);
        }

        log.info("Asignación masiva: {} subprocesos ({}) -> usuario {}",
                asignaciones.size(), asignaciones.get(0).getTipoProgreso().getNombreProgreso(), usuario.getEmail());

        return asignaciones.stream().map(this::toResponse).toList();
    }

    /** Recalcula Servicio.estado a partir del estado de TODOS sus subprocesos, no solo el que
     *  acaba de cambiar. Reemplaza el criterio anterior ("avanzarAEnEjecucionSiCorresponde"), que
     *  con UN solo subproceso asignado ya saltaba el servicio completo a EN_EJECUCION aunque los
     *  demás siguieran PENDIENTE — la tarjeta del gestor mostraba "en ejecución" con 3 de 4
     *  subprocesos sin arrancar (bug reportado). No toca servicios en CANCELADO/REPROGRAMADO
     *  (estados manuales, fuera de este ciclo automático) ni si no hay ningún subproceso activo
     *  (todos cancelados, o el servicio no tiene subprocesos todavía). */
    private void recalcularEstadoServicio(Servicio servicio, Usuario actor, String motivo) {
        if (servicio == null) return;
        // Todo cambio de estado de un subproceso puede cambiar el riesgo del caso (realizado /
        // pendiente / no realizado) — se recalcula después del commit.
        if (eventos != null) {
            eventos.publishEvent(new com.polygraph.erp.modules.riesgo.service.RecalculoRiesgoSolicitado(
                    servicio.getIdServicio(), actor != null ? actor.getIdUsuario() : null));
        }
        EstadoServicio estadoAnterior = servicio.getEstado();
        if (estadoAnterior == EstadoServicio.CANCELADO || estadoAnterior == EstadoServicio.REPROGRAMADO) return;

        List<ServicioSubproceso> activos = asignacionRepository
                .findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(servicio.getIdServicio()).stream()
                .filter(sp -> sp.getEstado() != EstadoAsignacion.CANCELADO)
                .toList();
        if (activos.isEmpty()) return;

        EstadoServicio nuevoEstado;
        if (activos.stream().allMatch(sp -> sp.getEstado() == EstadoAsignacion.PUBLICADO)) {
            nuevoEstado = EstadoServicio.FINALIZADO;
        } else if (activos.stream().anyMatch(sp -> sp.getEstado() != EstadoAsignacion.PENDIENTE)) {
            nuevoEstado = EstadoServicio.EN_EJECUCION;
        } else {
            // Todos los subprocesos activos volvieron a PENDIENTE (ej. se desasignó el último) —
            // si el servicio ya estaba EN_EJECUCION/FINALIZADO, retrocede a PENDIENTE; si seguía en
            // PENDIENTE/PROGRAMANDO, se deja tal cual (PROGRAMANDO señala que el candidato ya
            // diligenció el link — este método no le quita esa marca).
            nuevoEstado = (estadoAnterior == EstadoServicio.EN_EJECUCION || estadoAnterior == EstadoServicio.FINALIZADO)
                    ? EstadoServicio.PENDIENTE
                    : estadoAnterior;
        }

        if (nuevoEstado == estadoAnterior) return;

        servicio.setEstado(nuevoEstado);
        historialEstadoServicioRepository.save(HistorialEstadoServicio.builder()
                .servicio(servicio)
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(nuevoEstado)
                .usuario(actor)
                .fechaCambio(LocalDateTime.now())
                .observacion(motivo)
                .build());

        log.info("Servicio {} recalculado: {} -> {} ({})", servicio.getIdServicio(), estadoAnterior, nuevoEstado, motivo);
    }

    @Transactional
    public AsignacionResponse completar(Long id, Usuario actor, Set<Rol> rolesPermitidos) {
        ServicioSubproceso asignacion = obtenerOFallar(id);
        validarAlcance(asignacion.getTipoProgreso(), rolesPermitidos);
        validarEsDelEjecutor(asignacion, actor);
        if (asignacion.getEstado() != EstadoAsignacion.ASIGNADO) {
            throw new ApiException("Solo se puede completar una asignación en estado ASIGNADO", HttpStatus.CONFLICT);
        }
        validarFormularioDiligenciado(asignacion);
        List<String> sinJustificar = formularioService.respuestasCriticasSinJustificar(asignacion.getId());
        if (!sinJustificar.isEmpty()) {
            throw new ApiException("Hay respuestas determinantes sin explicación: " + String.join("; ", sinJustificar)
                    + ". Explica en qué te basas antes de enviarlo.", HttpStatus.CONFLICT);
        }
        asignacion.setEstado(EstadoAsignacion.COMPLETADO);
        asignacion.setFechaCompletado(LocalDateTime.now());
        // Se congela AQUÍ, al completar — no en generarSubprocesos() — porque mientras sigue
        // ASIGNADO el subproceso debe seguir la plantilla vigente (si admin agrega una sección
        // mientras alguien todavía lo está llenando, sí debe verla). Recién al completarse deja
        // de importar qué le pase a la plantilla después — ver FormularioService.obtener().
        asignacion.setVersionPlantilla(asignacion.getTipoProgreso().getVersionPlantilla());
        // Mismo criterio para el documento Word: se congela el archivo que esté activo justo en
        // este momento, para que generar/descargar el documento de este subproceso ya no cambie
        // aunque el administrador suba un Word distinto después (ver FormularioDocumentoService.
        // generar()). null si todavía no se ha subido ningún Word para este tipo de subproceso.
        formularioPlantillaConfigRepository.findById(asignacion.getTipoProgreso().getIdTipoProgreso())
                .ifPresent(config -> asignacion.setRutaWordCongelada(config.getPlantillaWordRuta()));

        historialAsignacionService.registrar(asignacion, AccionHistorialAsignacion.COMPLETADO,
                asignacion.getUsuarioAsignado(), asignacion.getUsuarioAsignado(),
                asignacion.getFechaProgramada(), asignacion.getFechaProgramada(), actor, null);
        recalcularEstadoServicio(asignacion.getServicio(), actor, "Subproceso completado.");
        notificarRevisorCompletado(asignacion, actor);

        return toResponse(asignacion);
    }

    /** Al completar, el subproceso queda esperando que alguien lo apruebe o lo devuelva — antes
     *  nadie se enteraba y quedaba COMPLETADO indefinidamente hasta que el gestor/programador
     *  entrara a revisar por su cuenta. Poligrafía/visita: lo revisa el programador responsable
     *  (si no hay, el gestor del cliente); analista: el gestor del cliente. */
    private void notificarRevisorCompletado(ServicioSubproceso asignacion, Usuario actor) {
        Rol rolResponsable = asignacion.getTipoProgreso().getRolResponsable();
        Servicio servicio = asignacion.getServicio();
        Long idGestor = servicio != null && servicio.getCliente() != null ? servicio.getCliente().getIdGestor() : null;
        Usuario programador = asignacion.getProgramadorResponsable();
        boolean presencial = rolResponsable == Rol.POLIGRAFISTA || rolResponsable == Rol.VISITADOR;
        Long idRevisor = presencial && programador != null ? programador.getIdUsuario() : idGestor;
        if (idRevisor == null || idRevisor.equals(actor.getIdUsuario())) return;

        var candidato = servicio != null ? servicio.getCandidato() : null;
        String evaluado = candidato != null
                ? (candidato.getNombres() + " " + (candidato.getApellidos() != null ? candidato.getApellidos() : "")).trim()
                : "";
        String nombreActor = (actor.getNombre() + " " + (actor.getApellido() != null ? actor.getApellido() : "")).trim();
        String nombreProceso = asignacion.getTipoProgreso().getNombreProgreso();

        usuarioRepository.findById(idRevisor)
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .ifPresent(revisor -> notificacionRepository.save(Notificacion.builder()
                        .usuario(revisor)
                        .tipo("SUBPROCESO_COMPLETADO")
                        .titulo(nombreProceso + " listo para revisar")
                        .mensaje(nombreActor + " completó \"" + nombreProceso + "\""
                                + (evaluado.isBlank() ? "" : " de " + evaluado) + " — revísalo para aprobarlo o devolverlo.")
                        .leida(false)
                        .fechaCreacion(LocalDateTime.now())
                        .realizadoPor(actor)
                        .referenciaTipo("SERVICIO_SUBPROCESO")
                        .referenciaId(asignacion.getId())
                        .build()));
    }

    /** Gestor/programador devuelve un subproceso COMPLETADO a quien lo diligenció — vuelve a
     *  ASIGNADO (misma persona, mismo horario) con la observación de qué corregir, y le llega
     *  una notificación. No borra lo ya guardado en el formulario: quien lo recibe de vuelta
     *  sigue viendo sus propias respuestas y las corrige desde ahí. */
    @Transactional
    public AsignacionResponse devolver(Long id, DevolverSubprocesoRequest req, Usuario actor, Set<Rol> rolesPermitidos) {
        ServicioSubproceso asignacion = obtenerOFallar(id);
        validarAlcance(asignacion.getTipoProgreso(), rolesPermitidos);
        com.polygraph.erp.shared.utils.AccesoGestor.validar(asignacion, actor);
        if (asignacion.getEstado() != EstadoAsignacion.COMPLETADO) {
            throw new ApiException("Solo se puede devolver un subproceso que esté completado.", HttpStatus.CONFLICT);
        }
        Usuario usuarioAsignado = asignacion.getUsuarioAsignado();
        if (usuarioAsignado == null) {
            throw new ApiException("Este subproceso no tiene un usuario asignado al cual devolvérselo.", HttpStatus.CONFLICT);
        }

        asignacion.setEstado(EstadoAsignacion.ASIGNADO);
        asignacion.setFechaCompletado(null);
        asignacion.setObservaciones(req.observacion());

        historialAsignacionService.registrar(asignacion, AccionHistorialAsignacion.DEVUELTO,
                usuarioAsignado, usuarioAsignado,
                asignacion.getFechaProgramada(), asignacion.getFechaProgramada(), actor, req.observacion());
        recalcularEstadoServicio(asignacion.getServicio(), actor, "Subproceso devuelto para corrección.");

        String nombreActor = actor.getNombre() != null ? actor.getNombre() : "Tu gestor";
        notificacionRepository.save(Notificacion.builder()
                .usuario(usuarioAsignado)
                .tipo("SUBPROCESO_DEVUELTO")
                .titulo(asignacion.getTipoProgreso().getNombreProgreso() + " devuelto")
                .mensaje(nombreActor + " te devolvió \"" + asignacion.getTipoProgreso().getNombreProgreso()
                        + "\" para que lo corrijas: " + req.observacion())
                .leida(false)
                .fechaCreacion(LocalDateTime.now())
                // Mismo criterio que FORMULARIO_ACTUALIZADO (ver FormularioService): el id del
                // SUBPROCESO, no del servicio — es lo que espera /formulario/:idServicioSubproceso
                // en el frontend (ver RUTA_POR_TIPO en Campana.jsx).
                .referenciaTipo("SERVICIO_SUBPROCESO")
                .referenciaId(asignacion.getId())
                .build());

        log.info("Subproceso devuelto: id={}, actor={}, usuarioAsignado={}", id, actor.getIdUsuario(), usuarioAsignado.getIdUsuario());
        return toResponse(asignacion);
    }

    /** Corre la hora de un subproceso en curso sin cambiar de responsable — lo usa quien coordina
     *  al resolver una novedad (ej. "el candidato no llegó" -> se reprograma para mañana), ver
     *  NovedadSubprocesoService.resolver(). Mismas reglas que asignar(): no en el pasado y sin
     *  cruzarse con la agenda de quien lo ejecuta. Deja rastro en el historial (REPROGRAMADO) y
     *  le avisa a quien lo ejecuta. Devuelve la fecha programada anterior (puede ser null). */
    @Transactional
    public LocalDateTime reprogramar(ServicioSubproceso asignacion, LocalDateTime nuevaFecha, Usuario actor, String motivo) {
        if (asignacion.getEstado() != EstadoAsignacion.ASIGNADO) {
            throw new ApiException("Solo se puede reprogramar un subproceso que esté en curso (asignado).", HttpStatus.CONFLICT);
        }
        if (nuevaFecha == null) {
            throw new ApiException("Indica la nueva fecha y hora.", HttpStatus.BAD_REQUEST);
        }
        validarNoEnElPasado(nuevaFecha);
        Usuario ejecutor = asignacion.getUsuarioAsignado();
        if (ejecutor != null) {
            validarSinChoqueDeHorario(ejecutor, duracionEfectiva(asignacion), nuevaFecha, Set.of(asignacion.getId()));
        }

        LocalDateTime anterior = asignacion.getFechaProgramada();
        asignacion.setFechaProgramada(nuevaFecha);
        historialAsignacionService.registrar(asignacion, AccionHistorialAsignacion.REPROGRAMADO,
                ejecutor, ejecutor, anterior, nuevaFecha, actor, motivo);

        if (ejecutor != null && !ejecutor.getIdUsuario().equals(actor.getIdUsuario())) {
            String nombreProceso = asignacion.getTipoProgreso().getNombreProgreso();
            notificacionRepository.save(Notificacion.builder()
                    .usuario(ejecutor)
                    .tipo("SUBPROCESO_REPROGRAMADO")
                    .titulo(nombreProceso + " reprogramado")
                    .mensaje("\"" + nombreProceso + "\" quedó para el " + nuevaFecha.format(FORMATO_HORA)
                            + (motivo != null && !motivo.isBlank() ? " — " + motivo : ""))
                    .leida(false)
                    .fechaCreacion(LocalDateTime.now())
                    .realizadoPor(actor)
                    .referenciaTipo("SERVICIO_SUBPROCESO")
                    .referenciaId(asignacion.getId())
                    .build());
        }
        log.info("Subproceso {} reprogramado: {} -> {} (actor {})", asignacion.getId(), anterior, nuevaFecha, actor.getIdUsuario());
        return anterior;
    }

    /** Quien coordina marca un subproceso como "No realizado" al resolver una novedad (ej. el
     *  candidato no aceptó la poligrafía, que es voluntaria). Queda CANCELADO con la marca y el
     *  motivo: para el motor no penaliza — su peso se reparte (docs/modelo-calificacion-riesgo.md §4).
     *  Solo mientras siga sin completarse (PENDIENTE o ASIGNADO). */
    @Transactional
    public void marcarNoRealizado(ServicioSubproceso asignacion, String motivo, Usuario actor) {
        if (asignacion.getEstado() != EstadoAsignacion.ASIGNADO && asignacion.getEstado() != EstadoAsignacion.PENDIENTE) {
            throw new ApiException("Solo se puede marcar como No realizado un subproceso que todavía no se ha completado.",
                    HttpStatus.CONFLICT);
        }
        Usuario ejecutor = asignacion.getUsuarioAsignado();
        asignacion.setEstado(EstadoAsignacion.CANCELADO);
        asignacion.setNoRealizado(true);
        asignacion.setMotivoNoRealizado(motivo);
        asignacion.setUsuarioNoRealizado(actor);
        asignacion.setFechaNoRealizado(LocalDateTime.now());
        historialAsignacionService.registrar(asignacion, AccionHistorialAsignacion.CANCELADO,
                ejecutor, ejecutor, asignacion.getFechaProgramada(), asignacion.getFechaProgramada(), actor,
                "No realizado: " + motivo);
        recalcularEstadoServicio(asignacion.getServicio(), actor, "Subproceso marcado como no realizado.");

        if (ejecutor != null && !ejecutor.getIdUsuario().equals(actor.getIdUsuario())) {
            String nombreProceso = asignacion.getTipoProgreso().getNombreProgreso();
            notificacionRepository.save(Notificacion.builder()
                    .usuario(ejecutor)
                    .tipo("SUBPROCESO_CANCELADO")
                    .titulo(nombreProceso + " no realizado")
                    .mensaje("\"" + nombreProceso + "\" quedó como no realizado — ya no tienes que hacerlo. Motivo: " + motivo)
                    .leida(false)
                    .fechaCreacion(LocalDateTime.now())
                    .realizadoPor(actor)
                    .build());
        }
        log.info("Subproceso {} marcado como no realizado por {}", asignacion.getId(), actor.getIdUsuario());
    }

    /** Gestor/programador aprueba un subproceso COMPLETADO — pasa a PUBLICADO, estado final de
     *  verdad (a diferencia de COMPLETADO, que solo dice que quien lo diligenció ya terminó, sin
     *  que nadie más lo haya revisado todavía). No modifica el formulario ni sus datos. */
    @Transactional
    public AsignacionResponse aprobar(Long id, Usuario actor, Set<Rol> rolesPermitidos) {
        ServicioSubproceso asignacion = obtenerOFallar(id);
        validarAlcance(asignacion.getTipoProgreso(), rolesPermitidos);
        com.polygraph.erp.shared.utils.AccesoGestor.validar(asignacion, actor);
        if (asignacion.getEstado() != EstadoAsignacion.COMPLETADO) {
            throw new ApiException("Solo se puede aprobar un subproceso que esté completado.", HttpStatus.CONFLICT);
        }

        asignacion.setEstado(EstadoAsignacion.PUBLICADO);
        asignacion.setFechaPublicado(LocalDateTime.now());

        historialAsignacionService.registrar(asignacion, AccionHistorialAsignacion.PUBLICADO,
                asignacion.getUsuarioAsignado(), asignacion.getUsuarioAsignado(),
                asignacion.getFechaProgramada(), asignacion.getFechaProgramada(), actor, null);
        recalcularEstadoServicio(asignacion.getServicio(), actor, "Subproceso aprobado.");

        Usuario usuarioAsignado = asignacion.getUsuarioAsignado();
        if (usuarioAsignado != null) {
            notificacionRepository.save(Notificacion.builder()
                    .usuario(usuarioAsignado)
                    .tipo("SUBPROCESO_PUBLICADO")
                    .titulo(asignacion.getTipoProgreso().getNombreProgreso() + " aprobado")
                    .mensaje("Tu trabajo en \"" + asignacion.getTipoProgreso().getNombreProgreso() + "\" quedó aprobado.")
                    .leida(false)
                    .fechaCreacion(LocalDateTime.now())
                    .referenciaTipo("SERVICIO_SUBPROCESO")
                    .referenciaId(asignacion.getId())
                    .build());
        }

        log.info("Subproceso aprobado/publicado: id={}, actor={}", id, actor.getIdUsuario());
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
        com.polygraph.erp.shared.utils.AccesoGestor.validar(asignacion, actor);

        Usuario usuarioAnterior = asignacion.getUsuarioAsignado();
        LocalDateTime fechaAnterior = asignacion.getFechaProgramada();

        asignacion.setUsuarioAsignado(null);
        asignacion.setFechaProgramada(null);
        asignacion.setFechaAsignacion(null);
        asignacion.setEstado(EstadoAsignacion.PENDIENTE);

        historialAsignacionService.registrar(asignacion, AccionHistorialAsignacion.DESASIGNADO,
                usuarioAnterior, null, fechaAnterior, null, actor, null);
        recalcularEstadoServicio(asignacion.getServicio(), actor, "Subproceso desasignado.");

        if (usuarioAnterior != null) {
            notificacionRepository.save(Notificacion.builder()
                    .usuario(usuarioAnterior)
                    .tipo("SUBPROCESO_DESASIGNADO")
                    .titulo(asignacion.getTipoProgreso().getNombreProgreso() + " desasignado")
                    .mensaje("Ya no tienes a cargo \"" + asignacion.getTipoProgreso().getNombreProgreso() + "\" — te lo quitaron de tu agenda.")
                    .leida(false)
                    .fechaCreacion(LocalDateTime.now())
                    .build());
        }

        return toResponse(asignacion);
    }

    @Transactional
    public AsignacionResponse cancelar(Long id, Usuario actor, Set<Rol> rolesPermitidos) {
        ServicioSubproceso asignacion = obtenerOFallar(id);
        validarAlcance(asignacion.getTipoProgreso(), rolesPermitidos);
        com.polygraph.erp.shared.utils.AccesoGestor.validar(asignacion, actor);
        Usuario usuarioAsignado = asignacion.getUsuarioAsignado();
        asignacion.setEstado(EstadoAsignacion.CANCELADO);

        historialAsignacionService.registrar(asignacion, AccionHistorialAsignacion.CANCELADO,
                asignacion.getUsuarioAsignado(), asignacion.getUsuarioAsignado(),
                asignacion.getFechaProgramada(), asignacion.getFechaProgramada(), actor, null);
        recalcularEstadoServicio(asignacion.getServicio(), actor, "Subproceso cancelado.");

        if (usuarioAsignado != null) {
            notificacionRepository.save(Notificacion.builder()
                    .usuario(usuarioAsignado)
                    .tipo("SUBPROCESO_CANCELADO")
                    .titulo(asignacion.getTipoProgreso().getNombreProgreso() + " cancelado")
                    .mensaje("\"" + asignacion.getTipoProgreso().getNombreProgreso() + "\" fue cancelado — ya no tienes que hacerlo.")
                    .leida(false)
                    .fechaCreacion(LocalDateTime.now())
                    .build());
        }

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
        com.polygraph.erp.shared.utils.AccesoGestor.validar(asignacion, actor);
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
    public List<HistorialAsignacionResponse> historial(Long id, Usuario actor, Set<Rol> rolesPermitidos) {
        ServicioSubproceso asignacion = obtenerOFallar(id);
        validarAlcance(asignacion.getTipoProgreso(), rolesPermitidos);
        com.polygraph.erp.shared.utils.AccesoGestor.validar(asignacion, actor);
        validarEsDelEjecutor(asignacion, actor);
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
        validarCandidatoCompleto(servicio);

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

    private static final Set<Rol> ROLES_EJECUTORES = Set.of(Rol.ANALISTA_INTERNO, Rol.POLIGRAFISTA, Rol.VISITADOR);

    /** validarAlcance solo mira el ROL del subproceso — para analista/poligrafista/visitador eso
     *  no basta: un poligrafista podía completar (o ver el historial de) el subproceso de OTRO
     *  poligrafista con solo conocer su id, llamando la API directo. Estos roles solo actúan
     *  sobre lo que tienen asignado a su nombre. GESTOR/PROGRAMADOR no se ven afectados. */
    private void validarEsDelEjecutor(ServicioSubproceso asignacion, Usuario actor) {
        if (actor == null || !ROLES_EJECUTORES.contains(actor.getRol())) return;
        Usuario asignado = asignacion.getUsuarioAsignado();
        if (asignado == null || !asignado.getIdUsuario().equals(actor.getIdUsuario())) {
            log.warn("Acceso denegado: usuario {} ({}) intentó operar el subproceso {} asignado a {}",
                    actor.getIdUsuario(), actor.getRol(), asignacion.getId(), asignado != null ? asignado.getIdUsuario() : null);
            throw new ApiException("Este subproceso no está asignado a ti.", HttpStatus.FORBIDDEN);
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
                a.getFechaPublicado(),
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
