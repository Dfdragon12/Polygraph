package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.estados.SemaforoEstados;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arma la consulta de subprocesos del semáforo según el perfil de quien pregunta (SE-04). Es el ÚNICO lugar donde se decide el
 * alcance: cliente = su empresa; gestor = los clientes que atiende; administrador = todo; analista, poligrafista y visitador =
 * lo que tienen asignado; programador = lo que coordina. Pura (devuelve texto JPQL y parámetros), así se prueba sin base de datos.
 */
public final class ConsultaSemaforo {

    private ConsultaSemaforo() {}

    /**
     * Filtros combinables: tiempo + cliente + responsable + rol + tipo de subproceso (el estado va aparte, es el semáforo).
     * Los de cliente y responsable solo los usan los perfiles internos.
     */
    public record Filtros(EstadoTiempo tiempo, Integer idCliente, Long idResponsable, Rol rol, Integer idTipoProgreso) {
        public static Filtros ninguno() { return new Filtros(null, null, null, null, null); }
    }

    /** Quién pregunta. {@code idCliente}: empresa del usuario cliente; {@code idUsuario}: el usuario interno. */
    public record Solicitante(Rol rol, Long idUsuario, Integer idCliente) {}

    public record Consulta(String jpql, Map<String, Object> parametros) {}

    /** Las filas del semáforo: servicio, subproceso y estado. */
    public static Consulta filas(Solicitante quien, Filtros f) {
        return armar("SELECT sp.servicio.idServicio, sp.estado, sp.id", quien, f);
    }

    /** Las filas con lo que necesitan las alertas aparte (SE-09): responsable, fecha, tipo, tiempo del servicio e «iniciado». */
    public static Consulta filasParaAlertas(Solicitante quien, Filtros f) {
        return armar("SELECT sp.servicio.idServicio, sp.id, sp.estado, u.idUsuario, sp.fechaProgramada, sp.tipoProgreso.rolResponsable, "
                + "sp.servicio.estadoTiempo, sp.servicio.diasRestantesHabiles, sp.iniciado", quien, f);
    }

    private static Consulta armar(String seleccion, Solicitante quien, Filtros f) {
        StringBuilder q = new StringBuilder(seleccion + " FROM ServicioSubproceso sp LEFT JOIN sp.usuarioAsignado u WHERE 1=1");
        Map<String, Object> p = new LinkedHashMap<>();
        List<String> condiciones = new ArrayList<>();

        switch (quien.rol()) {
            case ADMIN_CLIENTE, ANALISTA_CLIENTE -> {
                condiciones.add("sp.servicio.cliente.idCliente = :alcanceCliente");
                p.put("alcanceCliente", quien.idCliente());
            }
            case GESTOR -> {
                condiciones.add("sp.servicio.cliente.idGestor = :alcanceGestor");
                p.put("alcanceGestor", quien.idUsuario());
            }
            case ADMIN_POLYGRAPH -> { /* todo */ }
            case PROGRAMADOR -> {
                condiciones.add("sp.programadorResponsable.idUsuario = :alcanceProgramador");
                p.put("alcanceProgramador", quien.idUsuario());
            }
            case ANALISTA_INTERNO, POLIGRAFISTA, VISITADOR -> {
                condiciones.add("u.idUsuario = :alcanceResponsable");
                p.put("alcanceResponsable", quien.idUsuario());
            }
            default -> {
                // un perfil sin semáforo (p. ej. el candidato) no ve nada
                condiciones.add("1 = 0");
            }
        }
        if (SemaforoEstados.cuentaSubprocesos(quien.rol())) {
            condiciones.add("sp.estado IN :estadosVisibles");
            p.put("estadosVisibles", SemaforoEstados.estadosVisibles(quien.rol()));
        }

        boolean interno = quien.rol() == Rol.GESTOR || quien.rol() == Rol.ADMIN_POLYGRAPH;
        if (f.tiempo() != null) {
            condiciones.add("sp.servicio.estadoTiempo = :fTiempo");
            p.put("fTiempo", f.tiempo());
        }
        if (interno && f.idCliente() != null) {
            condiciones.add("sp.servicio.cliente.idCliente = :fCliente");
            p.put("fCliente", f.idCliente());
        }
        if (interno && f.idResponsable() != null) {
            condiciones.add("u.idUsuario = :fResponsable");
            p.put("fResponsable", f.idResponsable());
        }
        if (f.rol() != null) {
            condiciones.add("sp.tipoProgreso.rolResponsable = :fRol");
            p.put("fRol", f.rol());
        }
        if (f.idTipoProgreso() != null) {
            condiciones.add("sp.tipoProgreso.idTipoProgreso = :fTipo");
            p.put("fTipo", f.idTipoProgreso());
        }
        for (String c : condiciones) q.append(" AND ").append(c);
        return new Consulta(q.toString(), p);
    }
}
