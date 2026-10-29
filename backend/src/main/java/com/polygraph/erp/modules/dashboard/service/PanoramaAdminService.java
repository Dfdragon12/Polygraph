package com.polygraph.erp.modules.dashboard.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.dashboard.dto.PanoramaAdminResponse;
import com.polygraph.erp.modules.dashboard.dto.PanoramaAdminResponse.Candidatos;
import com.polygraph.erp.modules.dashboard.dto.PanoramaAdminResponse.Clientes;
import com.polygraph.erp.modules.dashboard.dto.PanoramaAdminResponse.Conteo;
import com.polygraph.erp.modules.dashboard.dto.PanoramaAdminResponse.Detalle;
import com.polygraph.erp.modules.dashboard.dto.PanoramaAdminResponse.Dia;
import com.polygraph.erp.modules.dashboard.dto.PanoramaAdminResponse.Dinero;
import com.polygraph.erp.modules.dashboard.dto.PanoramaAdminResponse.Item;
import com.polygraph.erp.modules.dashboard.dto.PanoramaAdminResponse.Sistema;
import com.polygraph.erp.modules.dashboard.dto.PanoramaAdminResponse.Solicitudes;
import com.polygraph.erp.modules.servicios.entity.EstadoLink;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.shared.enums.EstadoAprobacion;
import com.polygraph.erp.shared.enums.EstadoFacturaPospago;
import com.polygraph.erp.shared.enums.EstadoNovedad;
import com.polygraph.erp.shared.enums.EstadoPago;
import com.polygraph.erp.shared.enums.EstadoRevisionDocumento;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoSoporte;
import com.polygraph.erp.shared.enums.EstadoTrabajo;
import com.polygraph.erp.shared.enums.OrigenMensaje;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.enums.TipoPersona;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Panorama de todo el sistema para el administrador y detalle de cada tema (lo que se abre al hacer
 *  clic en un recuadro). Solo lectura: son conteos y listas cortas, aptos para pedirse en cada aviso
 *  en vivo. */
@Service
public class PanoramaAdminService {

    static final Set<Integer> PERIODOS = Set.of(7, 30, 90);
    static final int TOPE_RANKING = 6;
    static final int TOPE_DETALLE = 8;

    @PersistenceContext
    private EntityManager em;

    // ───────────────────────────── Panorama ─────────────────────────────

    @Transactional(readOnly = true)
    public PanoramaAdminResponse obtener(int dias) {
        if (!PERIODOS.contains(dias)) {
            throw new ApiException("El periodo debe ser de 7, 30 o 90 días.", HttpStatus.BAD_REQUEST);
        }
        LocalDate hasta = LocalDate.now();
        LocalDate desde = hasta.minusDays(dias - 1L);
        LocalDateTime desdeHora = desde.atStartOfDay();
        return new PanoramaAdminResponse(dias, desde, hasta,
                solicitudes(desde, hasta, dias),
                ranking("SELECT s.proceso.nombreProceso, COUNT(s) FROM Servicio s WHERE s.fechaSolicitud >= :d "
                        + "GROUP BY s.proceso.nombreProceso ORDER BY COUNT(s) DESC", "d", desde),
                topClientes(desde),
                clientes(desde, desdeHora),
                candidatos(desdeHora),
                dinero(desdeHora),
                sistema(desdeHora));
    }

    private Solicitudes solicitudes(LocalDate desde, LocalDate hasta, int dias) {
        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (EstadoServicio e : EstadoServicio.values()) porEstado.put(e.name(), 0L);
        long creadas = 0;
        for (Object[] f : filas("SELECT s.estado, COUNT(s) FROM Servicio s WHERE s.fechaSolicitud >= :d GROUP BY s.estado", "d", desde)) {
            long n = ((Number) f[1]).longValue();
            porEstado.put(((EstadoServicio) f[0]).name(), n);
            creadas += n;
        }

        Map<LocalDate, Long> creadasPorDia = new HashMap<>();
        for (Object[] f : filas("SELECT s.fechaSolicitud, COUNT(s) FROM Servicio s WHERE s.fechaSolicitud >= :d GROUP BY s.fechaSolicitud", "d", desde)) {
            creadasPorDia.put((LocalDate) f[0], ((Number) f[1]).longValue());
        }

        // Publicadas dentro del periodo (cada cambio a PUBLICADO) y cuántos días tardaron desde la solicitud.
        Map<LocalDate, Long> cerradasPorDia = new HashMap<>();
        long publicadas = 0, sumaDias = 0;
        for (Object[] f : filas("SELECT h.fechaCambio, h.servicio.fechaSolicitud FROM HistorialEstadoServicio h "
                + "WHERE h.estadoNuevo = :e AND h.fechaCambio >= :d", "e", EstadoServicio.PUBLICADO, "d", desde.atStartOfDay())) {
            LocalDate dia = ((LocalDateTime) f[0]).toLocalDate();
            cerradasPorDia.merge(dia, 1L, Long::sum);
            publicadas++;
            if (f[1] != null) sumaDias += Math.max(0, ChronoUnit.DAYS.between((LocalDate) f[1], dia));
        }

        Map<LocalDate, Long> pagadoPorDia = new HashMap<>();
        for (Object[] f : filas("SELECT p.fechaActualizacion, p.montoEnCentavos FROM PagoWompi p WHERE p.estado = :e AND p.fechaActualizacion >= :d",
                "e", EstadoPago.APROBADO, "d", desde.atStartOfDay())) {
            pagadoPorDia.merge(((LocalDateTime) f[0]).toLocalDate(), ((Number) f[1]).longValue(), Long::sum);
        }

        List<Dia> serie = new ArrayList<>();
        for (int i = 0; i < dias; i++) {
            LocalDate dia = desde.plusDays(i);
            serie.add(new Dia(dia, creadasPorDia.getOrDefault(dia, 0L), cerradasPorDia.getOrDefault(dia, 0L), pagadoPorDia.getOrDefault(dia, 0L)));
        }
        long canceladas = porEstado.get(EstadoServicio.CANCELADO.name());
        int pct = creadas == 0 ? 0 : (int) Math.round(100.0 * Math.min(publicadas, creadas) / creadas);
        double promedio = publicadas == 0 ? 0 : Math.round(10.0 * sumaDias / publicadas) / 10.0;
        return new Solicitudes(creadas, publicadas, canceladas, pct, promedio, porEstado, serie);
    }

    private List<Conteo> topClientes(LocalDate desde) {
        List<Conteo> r = new ArrayList<>();
        for (Object[] f : em.createQuery("SELECT s.cliente, COUNT(s) FROM Servicio s WHERE s.fechaSolicitud >= :d "
                        + "GROUP BY s.cliente ORDER BY COUNT(s) DESC", Object[].class)
                .setParameter("d", desde).setMaxResults(TOPE_RANKING).getResultList()) {
            r.add(new Conteo(nombreCliente((Cliente) f[0]), ((Number) f[1]).longValue()));
        }
        return r;
    }

    private List<Conteo> ranking(String jpql, Object... parametros) {
        var q = em.createQuery(jpql, Object[].class);
        for (int i = 0; i < parametros.length; i += 2) q.setParameter((String) parametros[i], parametros[i + 1]);
        List<Conteo> r = new ArrayList<>();
        for (Object[] f : q.setMaxResults(TOPE_RANKING).getResultList()) {
            r.add(new Conteo(String.valueOf(f[0]), ((Number) f[1]).longValue()));
        }
        return r;
    }

    private Clientes clientes(LocalDate desde, LocalDateTime desdeHora) {
        return new Clientes(
                contar("SELECT COUNT(c) FROM Cliente c"),
                contar("SELECT COUNT(c) FROM Cliente c WHERE c.estado = 'ACTIVO'"),
                contar("SELECT COUNT(c) FROM Cliente c WHERE c.tipoCliente = :t", "t", TipoCliente.PREPAGO),
                contar("SELECT COUNT(c) FROM Cliente c WHERE c.tipoCliente = :t", "t", TipoCliente.POSPAGO),
                contar("SELECT COUNT(c) FROM Cliente c WHERE c.tipoPersona = :t", "t", TipoPersona.JURIDICA),
                contar("SELECT COUNT(c) FROM Cliente c WHERE c.tipoPersona = :t", "t", TipoPersona.NATURAL),
                contar("SELECT COUNT(c) FROM Cliente c WHERE c.fechaRegistro >= :d", "d", desdeHora),
                contar("SELECT COUNT(c) FROM Cliente c WHERE c.estado = 'ACTIVO' AND c.idGestor IS NULL"),
                contar("SELECT COUNT(DISTINCT s.cliente) FROM Servicio s WHERE s.fechaSolicitud >= :d", "d", desde),
                contar("SELECT COUNT(m) FROM MensajeClienteGestor m WHERE m.leido = false AND m.origen = :o", "o", OrigenMensaje.CLIENTE));
    }

    private Candidatos candidatos(LocalDateTime desdeHora) {
        long linksGenerados = contar("SELECT COUNT(l) FROM LinkCandidato l WHERE l.fechaCreacion >= :d", "d", desdeHora);
        long usados = contar("SELECT COUNT(l) FROM LinkCandidato l WHERE l.fechaCreacion >= :d AND l.usado = true", "d", desdeHora);
        long bloqueados = contar("SELECT COUNT(l) FROM LinkCandidato l WHERE l.revocado = false AND l.intentosFallidos >= :m",
                "m", EstadoLink.MAX_INTENTOS);
        long revocados = contar("SELECT COUNT(l) FROM LinkCandidato l WHERE l.revocado = true AND l.fechaCreacion >= :d", "d", desdeHora);
        long porUsar = contar("SELECT COUNT(l) FROM LinkCandidato l WHERE l.revocado = false AND l.usado = false "
                + "AND l.intentosFallidos < :m AND l.servicio.estado NOT IN :cerrados",
                "m", EstadoLink.MAX_INTENTOS, "cerrados", EstadoLink.SERVICIO_CERRADO);
        long hojas = contar("SELECT COUNT(h) FROM HojaVidaEvaluado h WHERE h.completado = true AND h.fechaCompletado >= :d", "d", desdeHora);
        long porRevisar = contar("SELECT COUNT(x) FROM DocumentoEvaluado x WHERE x.estadoRevision = :e", "e", EstadoRevisionDocumento.PENDIENTE);
        long devueltos = contar("SELECT COUNT(x) FROM DocumentoEvaluado x WHERE x.estadoRevision NOT IN :ok",
                "ok", List.of(EstadoRevisionDocumento.PENDIENTE, EstadoRevisionDocumento.APROBADO));
        return new Candidatos(linksGenerados, usados, bloqueados, revocados, porUsar, hojas, porRevisar, devueltos);
    }

    private Dinero dinero(LocalDateTime desdeHora) {
        Object[] ok = fila("SELECT COUNT(p), COALESCE(SUM(p.montoEnCentavos), 0L) FROM PagoWompi p WHERE p.estado = :e AND p.fechaActualizacion >= :d",
                "e", EstadoPago.APROBADO, "d", desdeHora);
        long rechazados = contar("SELECT COUNT(p) FROM PagoWompi p WHERE p.estado IN :e AND p.fechaActualizacion >= :d",
                "e", List.of(EstadoPago.RECHAZADO, EstadoPago.ERROR, EstadoPago.ANULADO), "d", desdeHora);
        long pendientes = contar("SELECT COUNT(p) FROM PagoWompi p WHERE p.estado = :e", "e", EstadoPago.PENDIENTE);
        long emitidas = contar("SELECT COUNT(f) FROM FacturaPospago f WHERE f.fechaGeneracion >= :d", "d", desdeHora);
        long pagadas = contar("SELECT COUNT(f) FROM FacturaPospago f WHERE f.estado = :e AND f.fechaPago >= :d",
                "e", EstadoFacturaPospago.PAGADA, "d", desdeHora);
        return new Dinero(((Number) ok[0]).longValue(), ((Number) ok[1]).longValue(), rechazados, pendientes, emitidas, pagadas);
    }

    private Sistema sistema(LocalDateTime desdeHora) {
        Set<Rol> internos = Set.of(Rol.ADMIN_POLYGRAPH, Rol.GESTOR, Rol.ANALISTA_INTERNO, Rol.PROGRAMADOR, Rol.POLIGRAFISTA, Rol.VISITADOR);
        return new Sistema(
                contar("SELECT COUNT(p) FROM Proceso p WHERE p.activo = true"),
                contar("SELECT COUNT(p) FROM Proceso p"),
                contar("SELECT COUNT(c) FROM Ciudad c"),
                contar("SELECT COUNT(c) FROM CodigoCalificacion c WHERE c.activo = true"),
                contar("SELECT COUNT(t) FROM TipoNovedad t WHERE t.activo = true"),
                contar("SELECT COUNT(e) FROM EnlaceExterno e WHERE e.activo = true"),
                contar("SELECT COUNT(u) FROM Usuario u WHERE u.activo = true AND u.rol IN :r", "r", internos),
                contar("SELECT COUNT(u) FROM Usuario u WHERE u.activo = false AND u.rol IN :r", "r", internos),
                contar("SELECT COUNT(t) FROM TrabajoAsincrono t WHERE t.estado = :e AND t.fechaFin >= :d", "e", EstadoTrabajo.COMPLETADO, "d", desdeHora),
                contar("SELECT COUNT(t) FROM TrabajoAsincrono t WHERE t.estado = :e", "e", EstadoTrabajo.FALLIDO));
    }

    // ───────────────────────────── Detalle de un tema ─────────────────────────────

    /** Las filas más importantes de un tema, para abrirlas sin salir del tablero. Temas: reversiones, documentos,
     *  novedades, trabajos, facturas, pagos, ciudades, sinGestor, vencidas, porVencer y estado-XXXX. */
    @Transactional(readOnly = true)
    public Detalle detalle(String tema) {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDate hoy = LocalDate.now();
        return switch (tema) {
            case "reversiones" -> detalle(tema, "Reversiones por resolver", "/admin/reversiones",
                    "SELECT r FROM ReversionSolicitud r WHERE r.estado = :e ORDER BY r.fechaSolicitud ASC",
                    r -> item("Solicitud #" + ((com.polygraph.erp.modules.servicios.entity.ReversionSolicitud) r).getServicio().getIdServicio()
                                    + " → " + ((com.polygraph.erp.modules.servicios.entity.ReversionSolicitud) r).getEstadoDeseado(),
                            ((com.polygraph.erp.modules.servicios.entity.ReversionSolicitud) r).getMotivo(),
                            ((com.polygraph.erp.modules.servicios.entity.ReversionSolicitud) r).getFechaSolicitud(), "/admin/reversiones"),
                    "e", EstadoAprobacion.PENDIENTE);
            case "documentos" -> detalle(tema, "Documentos de clientes por revisar", "/admin/clientes",
                    "SELECT x FROM SoporteCliente x WHERE x.estado = :e ORDER BY x.fechaEntrega ASC",
                    x -> {
                        var s = (com.polygraph.erp.modules.clientes.entity.SoporteCliente) x;
                        return item(String.valueOf(s.getTipoSoporte()), nombreCliente(s.getCliente()),
                                s.getFechaEntrega() != null ? s.getFechaEntrega().atStartOfDay() : null, "/admin/clientes");
                    }, "e", EstadoSoporte.PENDIENTE);
            case "novedades" -> detalle(tema, "Novedades abiertas", "/admin/semaforo",
                    "SELECT n FROM NovedadSubproceso n WHERE n.estado = :e ORDER BY n.fechaRegistro ASC",
                    n -> {
                        var v = (com.polygraph.erp.modules.servicios.entity.NovedadSubproceso) n;
                        return item(v.getTipo() != null ? v.getTipo().getNombre() : "Novedad", recortar(v.getDescripcion()),
                                v.getFechaRegistro(), "/admin/semaforo");
                    }, "e", EstadoNovedad.ABIERTA);
            case "trabajos" -> detalle(tema, "Trabajos en segundo plano fallidos", "/admin/trabajos",
                    "SELECT t FROM TrabajoAsincrono t WHERE t.estado = :e ORDER BY t.fechaCreacion DESC",
                    t -> {
                        var v = (com.polygraph.erp.modules.trabajos.entity.TrabajoAsincrono) t;
                        return item(String.valueOf(v.getTipo()), recortar(v.getMensajeUsuario()), v.getFechaCreacion(), "/admin/trabajos");
                    }, "e", EstadoTrabajo.FALLIDO);
            case "facturas" -> detalle(tema, "Facturas pospago vencidas", "/admin/facturacion",
                    "SELECT f FROM FacturaPospago f WHERE f.estado = :e ORDER BY f.fechaVencimiento ASC",
                    f -> {
                        var v = (com.polygraph.erp.modules.pagos.entity.FacturaPospago) f;
                        return item(v.getReferencia() + " · " + nombreCliente(v.getCliente()),
                                "$ " + String.format("%,.0f", v.getMontoTotal()) + " · venció el " + v.getFechaVencimiento(),
                                v.getFechaGeneracion(), "/admin/facturacion");
                    }, "e", EstadoFacturaPospago.VENCIDA);
            case "pagos" -> detalle(tema, "Pagos pendientes hace más de 1 hora", "/admin/facturacion",
                    "SELECT p FROM PagoWompi p WHERE p.estado = :e AND p.fechaCreacion < :l ORDER BY p.fechaCreacion ASC",
                    p -> {
                        var v = (com.polygraph.erp.modules.pagos.entity.PagoWompi) p;
                        return item(v.getReferencia() + " · " + nombreCliente(v.getCliente()),
                                "$ " + String.format("%,.0f", v.getMontoEnCentavos() / 100.0), v.getFechaCreacion(), "/admin/facturacion");
                    }, "e", EstadoPago.PENDIENTE, "l", ahora.minusHours(1));
            case "ciudades" -> detalle(tema, "Ciudades por confirmar", "/admin/ciudades",
                    "SELECT c FROM Ciudad c WHERE c.pendienteConfirmacion = true ORDER BY c.nombreCiudad",
                    c -> {
                        var v = (com.polygraph.erp.modules.catalogo.entity.Ciudad) c;
                        return item(v.getNombreCiudad(), v.getDepartamento(), null, "/admin/ciudades");
                    });
            case "sinGestor" -> detalle(tema, "Clientes activos sin gestor", "/admin/clientes",
                    "SELECT c FROM Cliente c WHERE c.estado = 'ACTIVO' AND c.idGestor IS NULL ORDER BY c.fechaRegistro DESC",
                    c -> {
                        var v = (Cliente) c;
                        return item(nombreCliente(v), v.getEmailPrincipal(), v.getFechaRegistro(), "/admin/clientes");
                    });
            case "vencidas" -> servicios(tema, "Entregas vencidas", "SELECT s FROM Servicio s WHERE s.estadoTiempo = :e "
                    + "ORDER BY s.diasRetrasoHabiles DESC, s.fechaCompromisoAjustada ASC", "e", com.polygraph.erp.shared.enums.EstadoTiempo.VENCIDA);
            case "porVencer" -> servicios(tema, "Entregas por vencer", "SELECT s FROM Servicio s WHERE s.estadoTiempo = :e "
                    + "ORDER BY s.fechaCompromisoAjustada ASC", "e", com.polygraph.erp.shared.enums.EstadoTiempo.POR_VENCER);
            default -> {
                if (!tema.startsWith("estado-")) throw new ApiException("Tema desconocido.", HttpStatus.NOT_FOUND);
                EstadoServicio estado;
                try {
                    estado = EstadoServicio.valueOf(tema.substring("estado-".length()));
                } catch (IllegalArgumentException e) {
                    throw new ApiException("Estado desconocido.", HttpStatus.NOT_FOUND);
                }
                yield servicios(tema, "Solicitudes " + estado.name().toLowerCase().replace('_', ' '),
                        "SELECT s FROM Servicio s WHERE s.estado = :e ORDER BY s.fechaSolicitud DESC, s.idServicio DESC", "e", estado);
            }
        };
    }

    private Detalle servicios(String tema, String titulo, String jpql, Object... parametros) {
        return detalle(tema, titulo, "/admin/semaforo", jpql, o -> {
            Servicio s = (Servicio) o;
            String cargo = s.getCargo() != null && !s.getCargo().isBlank() ? " · " + s.getCargo() : "";
            String entrega = s.fechaParaMostrar() != null ? " · entrega " + s.fechaParaMostrar() : "";
            return item("Solicitud #" + s.getIdServicio() + " · " + s.getProceso().getNombreProceso(),
                    nombreCliente(s.getCliente()) + cargo + entrega,
                    s.getFechaSolicitud() != null ? s.getFechaSolicitud().atStartOfDay() : null, "/admin/semaforo");
        }, parametros);
    }

    private Detalle detalle(String tema, String titulo, String rutaVerTodo, String jpql,
                            java.util.function.Function<Object, Item> aItem, Object... parametros) {
        var q = em.createQuery(jpql, Object.class);
        var cuenta = em.createQuery(jpql.replaceFirst("SELECT \\w+ FROM", "SELECT COUNT(1) FROM").replaceAll(" ORDER BY .*$", ""), Long.class);
        for (int i = 0; i < parametros.length; i += 2) {
            q.setParameter((String) parametros[i], parametros[i + 1]);
            cuenta.setParameter((String) parametros[i], parametros[i + 1]);
        }
        List<Item> items = q.setMaxResults(TOPE_DETALLE).getResultList().stream().map(aItem).toList();
        return new Detalle(tema, titulo, cuenta.getSingleResult(), items, rutaVerTodo);
    }

    private static Item item(String titulo, String detalle, LocalDateTime fecha, String ruta) {
        return new Item(titulo, detalle, fecha, ruta);
    }

    private static String recortar(String texto) {
        if (texto == null) return null;
        String t = texto.strip();
        return t.length() > 140 ? t.substring(0, 137) + "…" : t;
    }

    static String nombreCliente(Cliente c) {
        if (c == null) return "—";
        String n = TipoPersona.JURIDICA.equals(c.getTipoPersona())
                ? (c.getNombreComercial() != null && !c.getNombreComercial().isBlank() ? c.getNombreComercial() : c.getRazonSocial())
                : ((c.getNombre() != null ? c.getNombre() : "") + " " + (c.getApellido() != null ? c.getApellido() : "")).trim();
        return n == null || n.isBlank() ? String.valueOf(c.getEmailPrincipal()) : n;
    }

    // ───────────────────────────── Apoyo ─────────────────────────────

    private long contar(String jpql, Object... parametros) {
        return ((Number) fila(jpql, parametros)[0]).longValue();
    }

    private Object[] fila(String jpql, Object... parametros) {
        List<Object[]> r = filas(jpql, parametros);
        return r.isEmpty() ? new Object[]{0L, 0L} : r.get(0);
    }

    private List<Object[]> filas(String jpql, Object... parametros) {
        var q = em.createQuery(jpql, Object.class);
        for (int i = 0; i < parametros.length; i += 2) q.setParameter((String) parametros[i], parametros[i + 1]);
        return q.getResultList().stream().map(o -> o instanceof Object[] f ? f : new Object[]{o}).toList();
    }
}
