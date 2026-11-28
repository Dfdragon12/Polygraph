package com.polygraph.erp.modules.dashboard.service;

import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.dashboard.dto.DashboardAdminResponse;
import com.polygraph.erp.modules.dashboard.dto.DashboardAdminResponse.Atencion;
import com.polygraph.erp.modules.dashboard.dto.DashboardAdminResponse.CargaDto;
import com.polygraph.erp.modules.dashboard.dto.DashboardAdminResponse.DiaDto;
import com.polygraph.erp.modules.dashboard.dto.DashboardAdminResponse.Equipo;
import com.polygraph.erp.modules.dashboard.dto.DashboardAdminResponse.Finanzas;
import com.polygraph.erp.modules.dashboard.dto.DashboardAdminResponse.Riesgo;
import com.polygraph.erp.modules.dashboard.dto.DashboardAdminResponse.RolDto;
import com.polygraph.erp.modules.dashboard.dto.DashboardAdminResponse.Servicios;
import com.polygraph.erp.modules.dashboard.dto.DashboardAdminResponse.Trabajo;
import com.polygraph.erp.modules.dashboard.dto.ServiciosPorEstadoResponse;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.EstadoResultado;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.tiemporeal.CanalCambios;
import com.polygraph.erp.shared.enums.EstadoAprobacion;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoFacturaPospago;
import com.polygraph.erp.shared.enums.EstadoNovedad;
import com.polygraph.erp.shared.enums.EstadoPago;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoSoporte;
import com.polygraph.erp.shared.enums.EstadoTrabajo;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoPersona;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardAdminService {

    private static final Set<Rol> ROLES_INTERNOS = Set.of(
            Rol.ADMIN_POLYGRAPH, Rol.GESTOR, Rol.ANALISTA_INTERNO,
            Rol.PROGRAMADOR, Rol.POLIGRAFISTA, Rol.VISITADOR
    );
    private static final Set<Rol> ROLES_CLIENTES = Set.of(
            Rol.ADMIN_CLIENTE, Rol.ANALISTA_CLIENTE
    );

    static final int DIAS_GRAFICA = 14;
    static final int DIAS_RIESGO = 30;
    static final int HORAS_NOVEDAD_VIEJA = 48;
    static final int TOPE_CARGA = 6;

    private final UsuarioRepository usuarioRepository;
    private final ProcesoRepository procesoRepository;
    private final ServicioRepository servicioRepository;
    private final ClienteRepository clienteRepository;
    private final CanalCambios canal;
    private final com.polygraph.erp.modules.servicios.service.ResumenTiempoService resumenTiempoService;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public DashboardAdminResponse obtenerDashboard() {
        long totalActivos     = usuarioRepository.countByActivoTrue();
        long internosActivos  = usuarioRepository.countByActivoTrueAndRolIn(ROLES_INTERNOS);
        long clientesActivos  = usuarioRepository.countByActivoTrueAndRolIn(ROLES_CLIENTES);

        List<DashboardAdminResponse.UltimoLoginDto> ultimosLogins =
                usuarioRepository.findTop8ByUltimoAccesoIsNotNullOrderByUltimoAccesoDesc()
                        .stream()
                        .map(u -> new DashboardAdminResponse.UltimoLoginDto(
                                u.getIdUsuario(), u.getNombre(), u.getApellido(),
                                u.getEmail(), u.getRol().name(), u.getActivo(), u.getUltimoAcceso()
                        ))
                        .toList();

        Map<ClasificacionProceso, List<Proceso>> porClasificacion =
                procesoRepository.findAll().stream()
                        .collect(Collectors.groupingBy(Proceso::getClasificacion));

        List<DashboardAdminResponse.ServicioCategoriaDto> serviciosPorCategoria =
                porClasificacion.entrySet().stream()
                        .map(e -> new DashboardAdminResponse.ServicioCategoriaDto(
                                e.getKey().getNombre(),
                                e.getValue().size(),
                                e.getValue().stream().filter(p -> Boolean.TRUE.equals(p.getActivo())).count()
                        ))
                        .sorted(Comparator.comparingLong(DashboardAdminResponse.ServicioCategoriaDto::total).reversed())
                        .toList();

        LocalDateTime ahora = LocalDateTime.now();
        return new DashboardAdminResponse(ahora, totalActivos, internosActivos, clientesActivos,
                ultimosLogins, serviciosPorCategoria,
                servicios(LocalDate.now()), trabajo(), atencion(ahora), riesgo(ahora), finanzas(ahora), equipo(ahora));
    }

    // ───────────────────────────── Servicios ─────────────────────────────

    private Servicios servicios(LocalDate hoy) {
        // Totales, en curso, vencidas y por vencer: del resumen único de tiempos (el mismo que usa el Semáforo y los demás tableros).
        var resumen = resumenTiempoService.resumir(com.polygraph.erp.modules.servicios.service.ResumenTiempoService.Filtro.TODO, 0);
        Map<String, Long> porEstado = new LinkedHashMap<>(resumen.porEtapa());
        long total = resumen.total();
        long enCurso = resumen.enCurso();
        long vencidas = resumen.vencidas();
        long porVencer = resumen.porVencer();

        long nuevasHoy = contar("SELECT COUNT(s) FROM Servicio s WHERE s.fechaSolicitud = :d", "d", hoy);
        long nuevasSemana = contar("SELECT COUNT(s) FROM Servicio s WHERE s.fechaSolicitud >= :d", "d", hoy.minusDays(6));
        long nuevasMes = contar("SELECT COUNT(s) FROM Servicio s WHERE s.fechaSolicitud >= :d", "d", hoy.withDayOfMonth(1));

        LocalDate desde = hoy.minusDays(DIAS_GRAFICA - 1L);
        Map<LocalDate, Long> porDia = new java.util.HashMap<>();
        for (Object[] f : consulta("SELECT s.fechaSolicitud, COUNT(s) FROM Servicio s WHERE s.fechaSolicitud >= :d GROUP BY s.fechaSolicitud",
                "d", desde)) {
            porDia.put((LocalDate) f[0], ((Number) f[1]).longValue());
        }
        List<DiaDto> serie = new ArrayList<>();
        for (int i = 0; i < DIAS_GRAFICA; i++) {
            LocalDate dia = desde.plusDays(i);
            serie.add(new DiaDto(dia, porDia.getOrDefault(dia, 0L)));
        }
        return new Servicios(total, enCurso, porEstado, nuevasHoy, nuevasSemana, nuevasMes, vencidas, porVencer, serie);
    }

    // ───────────────────────────── Trabajo del equipo ─────────────────────────────

    private Trabajo trabajo() {
        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (EstadoSubproceso e : List.of(EstadoSubproceso.PENDIENTE, EstadoSubproceso.EN_EJECUCION, EstadoSubproceso.FINALIZADO, EstadoSubproceso.PUBLICADO)) {
            porEstado.put(e.name(), 0L);
        }
        for (Object[] f : consulta("SELECT sp.estado, COUNT(sp) FROM ServicioSubproceso sp GROUP BY sp.estado")) {
            EstadoSubproceso est = (EstadoSubproceso) f[0];
            String clave = (est == EstadoSubproceso.PROGRAMANDO ? EstadoSubproceso.PENDIENTE : est).name();
            if (porEstado.containsKey(clave)) porEstado.merge(clave, ((Number) f[1]).longValue(), Long::sum);
        }

        Map<Rol, long[]> roles = new EnumMap<>(Rol.class); // [sinAsignar, enCurso, porAprobar]
        for (Object[] f : consulta("SELECT sp.tipoProgreso.rolResponsable, sp.estado, COUNT(sp) FROM ServicioSubproceso sp "
                + "WHERE sp.estado IN :abiertos GROUP BY sp.tipoProgreso.rolResponsable, sp.estado",
                "abiertos", List.of(EstadoSubproceso.PENDIENTE, EstadoSubproceso.PROGRAMANDO, EstadoSubproceso.EN_EJECUCION, EstadoSubproceso.REPROGRAMADO, EstadoSubproceso.FINALIZADO))) {
            if (f[0] == null) continue;
            long[] v = roles.computeIfAbsent((Rol) f[0], k -> new long[3]);
            long n = ((Number) f[2]).longValue();
            switch ((EstadoSubproceso) f[1]) {
                case PENDIENTE, PROGRAMANDO -> v[0] += n;
                case EN_EJECUCION, REPROGRAMADO -> v[1] += n;
                default -> v[2] += n;
            }
        }
        List<RolDto> porRol = roles.entrySet().stream()
                .map(e -> new RolDto(e.getKey().name(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();

        List<CargaDto> carga = new ArrayList<>();
        for (Object[] f : em.createQuery("SELECT u.idUsuario, u.nombre, u.apellido, u.rol, COUNT(sp) FROM ServicioSubproceso sp "
                        + "JOIN sp.usuarioAsignado u WHERE sp.estado = :e GROUP BY u.idUsuario, u.nombre, u.apellido, u.rol "
                        + "ORDER BY COUNT(sp) DESC", Object[].class)
                .setParameter("e", EstadoSubproceso.EN_EJECUCION).setMaxResults(TOPE_CARGA).getResultList()) {
            String nombre = (f[1] + (f[2] != null ? " " + f[2] : "")).trim();
            carga.add(new CargaDto((Long) f[0], nombre, ((Rol) f[3]).name(), ((Number) f[4]).longValue()));
        }
        return new Trabajo(porEstado, porRol, carga);
    }

    // ───────────────────────────── Atención ─────────────────────────────

    private Atencion atencion(LocalDateTime ahora) {
        long reversiones = contar("SELECT COUNT(r) FROM ReversionSolicitud r WHERE r.estado = :e", "e", EstadoAprobacion.PENDIENTE);
        long documentos = contar("SELECT COUNT(x) FROM SoporteCliente x WHERE x.estado = :e", "e", EstadoSoporte.PENDIENTE);
        long novedades = contar("SELECT COUNT(n) FROM NovedadSubproceso n WHERE n.estado = :e", "e", EstadoNovedad.ABIERTA);
        long novedadesViejas = contar("SELECT COUNT(n) FROM NovedadSubproceso n WHERE n.estado = :e AND n.fechaRegistro < :limite",
                "e", EstadoNovedad.ABIERTA, "limite", ahora.minusHours(HORAS_NOVEDAD_VIEJA));
        long ciudades = contar("SELECT COUNT(c) FROM Ciudad c WHERE c.pendienteConfirmacion = true");
        long fallidos = contar("SELECT COUNT(t) FROM TrabajoAsincrono t WHERE t.estado = :e", "e", EstadoTrabajo.FALLIDO);
        long enCola = contar("SELECT COUNT(t) FROM TrabajoAsincrono t WHERE t.estado = :e", "e", EstadoTrabajo.EN_COLA);
        long facturasVencidas = contar("SELECT COUNT(f) FROM FacturaPospago f WHERE f.estado = :e", "e", EstadoFacturaPospago.VENCIDA);
        // Un pago que lleva más de una hora PENDIENTE: el webhook no llegó (o el cliente abandonó).
        long pagosViejos = contar("SELECT COUNT(p) FROM PagoWompi p WHERE p.estado = :e AND p.fechaCreacion < :limite",
                "e", EstadoPago.PENDIENTE, "limite", ahora.minusHours(1));
        long sinGestor = contar("SELECT COUNT(c) FROM Cliente c WHERE c.estado = 'ACTIVO' AND c.idGestor IS NULL");
        return new Atencion(reversiones, documentos, novedades, novedadesViejas, ciudades, fallidos, enCola,
                facturasVencidas, pagosViejos, sinGestor);
    }

    // ───────────────────────────── Riesgo ─────────────────────────────

    private Riesgo riesgo(LocalDateTime ahora) {
        long bajo = 0, medio = 0, alto = 0;
        for (Object[] f : consulta("SELECT c.nivelDecidido, c.nivelFinal, COUNT(c) FROM CalificacionRiesgo c "
                + "WHERE c.vigente = true AND c.estado = :e AND c.fechaCalculo >= :desde GROUP BY c.nivelDecidido, c.nivelFinal",
                "e", EstadoResultado.CALCULADO, "desde", ahora.minusDays(DIAS_RIESGO))) {
            // El nivel que vale es el que decidió el gestor; si no hay, el automático.
            NivelRiesgo nivel = f[0] != null ? (NivelRiesgo) f[0] : (NivelRiesgo) f[1];
            long n = ((Number) f[2]).longValue();
            if (nivel == null) continue;
            switch (nivel) {
                case BAJO -> bajo += n;
                case MEDIO -> medio += n;
                case ALTO -> alto += n;
            }
        }
        return new Riesgo(bajo, medio, alto, bajo + medio + alto);
    }

    // ───────────────────────────── Dinero ─────────────────────────────

    private Finanzas finanzas(LocalDateTime ahora) {
        LocalDateTime inicioHoy = ahora.toLocalDate().atStartOfDay();
        LocalDateTime inicioMes = ahora.toLocalDate().withDayOfMonth(1).atStartOfDay();
        Object[] hoy = fila("SELECT COUNT(p), COALESCE(SUM(p.montoEnCentavos), 0L) FROM PagoWompi p WHERE p.estado = :e AND p.fechaActualizacion >= :d",
                "e", EstadoPago.APROBADO, "d", inicioHoy);
        Object[] mes = fila("SELECT COUNT(p), COALESCE(SUM(p.montoEnCentavos), 0L) FROM PagoWompi p WHERE p.estado = :e AND p.fechaActualizacion >= :d",
                "e", EstadoPago.APROBADO, "d", inicioMes);
        Object[] pendientes = fila("SELECT COUNT(f), COALESCE(SUM(f.montoTotal), 0) FROM FacturaPospago f WHERE f.estado = :e",
                "e", EstadoFacturaPospago.PENDIENTE);
        Object[] vencidas = fila("SELECT COUNT(f), COALESCE(SUM(f.montoTotal), 0) FROM FacturaPospago f WHERE f.estado = :e",
                "e", EstadoFacturaPospago.VENCIDA);
        return new Finanzas(
                ((Number) hoy[0]).longValue(), ((Number) hoy[1]).longValue(),
                ((Number) mes[0]).longValue(), ((Number) mes[1]).longValue(),
                ((Number) pendientes[0]).longValue(), dinero(pendientes[1]),
                ((Number) vencidas[0]).longValue(), dinero(vencidas[1]));
    }

    private static BigDecimal dinero(Object valor) {
        return valor instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(valor));
    }

    // ───────────────────────────── Equipo ─────────────────────────────

    private Equipo equipo(LocalDateTime ahora) {
        Map<String, Long> porRol = new LinkedHashMap<>();
        for (Rol r : List.of(Rol.GESTOR, Rol.ANALISTA_INTERNO, Rol.PROGRAMADOR, Rol.POLIGRAFISTA, Rol.VISITADOR, Rol.ADMIN_POLYGRAPH)) {
            porRol.put(r.name(), 0L);
        }
        for (Object[] f : consulta("SELECT u.rol, COUNT(u) FROM Usuario u WHERE u.activo = true AND u.rol IN :roles GROUP BY u.rol",
                "roles", ROLES_INTERNOS)) {
            porRol.put(((Rol) f[0]).name(), ((Number) f[1]).longValue());
        }
        CanalCambios.Conectados conectados = canal.conectados();
        long nuevosMes = contar("SELECT COUNT(c) FROM Cliente c WHERE c.fechaRegistro >= :d",
                "d", ahora.toLocalDate().withDayOfMonth(1).atStartOfDay());
        return new Equipo(porRol, conectados.internos(), conectados.clientes(), conectados.candidatos(), nuevosMes);
    }

    // ───────────────────────────── Apoyo ─────────────────────────────

    private long contar(String jpql, Object... parametros) {
        return ((Number) fila(jpql, parametros)[0]).longValue();
    }

    private Object[] fila(String jpql, Object... parametros) {
        List<Object[]> r = consulta(jpql, parametros);
        return r.isEmpty() ? new Object[]{0L, 0L} : r.get(0);
    }

    /** Consulta que siempre devuelve filas Object[] (incluso con una sola columna). */
    private List<Object[]> consulta(String jpql, Object... parametros) {
        var q = em.createQuery(jpql, Object.class);
        for (int i = 0; i < parametros.length; i += 2) q.setParameter((String) parametros[i], parametros[i + 1]);
        return q.getResultList().stream()
                .map(o -> o instanceof Object[] fila ? fila : new Object[]{o})
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiciosPorEstadoResponse serviciosPorEstado(Integer idCliente) {
        List<ServiciosPorEstadoResponse.ClienteResumenDto> clientes = clienteRepository.findAll()
                .stream()
                .map(c -> {
                    String nombre = TipoPersona.JURIDICA.equals(c.getTipoPersona())
                            ? c.getRazonSocial()
                            : (c.getNombre() != null ? c.getNombre() + " " + (c.getApellido() != null ? c.getApellido() : "") : c.getEmailPrincipal());
                    return new ServiciosPorEstadoResponse.ClienteResumenDto(c.getIdCliente(), nombre.trim());
                })
                .sorted(Comparator.comparing(ServiciosPorEstadoResponse.ClienteResumenDto::nombre))
                .toList();

        // Del resumen único de tiempos: las mismas cifras que el Resumen del administrador y los demás tableros.
        var resumen = resumenTiempoService.resumir(new com.polygraph.erp.modules.servicios.service.ResumenTiempoService.Filtro(idCliente, null, null, null), 0);
        List<ServiciosPorEstadoResponse.EstadoDto> estados = Arrays.stream(EstadoServicio.values())
                .map(e -> new ServiciosPorEstadoResponse.EstadoDto(e.name(), resumen.porEtapa().get(e.name())))
                .toList();
        long totalServicios = resumen.total();
        long vencidas = resumen.vencidas();
        return new ServiciosPorEstadoResponse(clientes, estados, totalServicios, vencidas);
    }
}
