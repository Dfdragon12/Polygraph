package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoServicio;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Filtros de la tabla "Solicitudes" del gestor cuando hay texto en el buscador: así busca en
 *  TODAS sus solicitudes, no solo en la página que tiene en pantalla. Mismo alcance y mismo
 *  criterio de pestañas que ServicioRepository.findByCliente_IdGestor... */
public final class ServicioSpecifications {

    private ServicioSpecifications() {}

    public static Specification<Servicio> delGestor(Long idGestor) {
        return (root, query, cb) -> cb.equal(root.get("cliente").get("idGestor"), idGestor);
    }

    public static Specification<Servicio> delCliente(Integer idCliente) {
        return (root, query, cb) -> cb.equal(root.get("cliente").get("idCliente"), idCliente);
    }

    /** Estado de tiempo: lee el que guardó SemaforoTiempoService (ya no calcula fechas aquí). */
    public static Specification<Servicio> conTiempo(java.util.Collection<com.polygraph.erp.shared.enums.EstadoTiempo> tiempos) {
        if (tiempos == null || tiempos.isEmpty()) return null;
        return (root, query, cb) -> root.get("estadoTiempo").in(tiempos);
    }

    /** Orden de las listas: por defecto lo más reciente primero; "retraso" pone primero lo más atrasado (más días hábiles de retraso). */
    public static org.springframework.data.domain.Sort orden(String orden) {
        // "retraso" lo ordena ordenadoPorRetraso (necesita un CASE por estado de tiempo, que un Sort por propiedad no sabe expresar).
        if ("retraso".equalsIgnoreCase(orden)) return org.springframework.data.domain.Sort.unsorted();
        return org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "fechaSolicitud", "horaSolicitud");
    }

    /**
     * «Más atrasadas primero» (AJ-04): vencidas (la de más días hábiles de retraso primero), luego por vencer, a tiempo, EN PAUSA y al final las ya entregadas.
     * Una solicitud en pausa no tiene fecha real todavía, así que nunca va por delante de una que sí corre. Va junto con {@link #orden(String)}, que en
     * este caso no ordena nada. No filtra: solo fija el ORDER BY de la consulta de datos (no el de la de conteo).
     */
    public static Specification<Servicio> ordenadoPorRetraso(String orden) {
        if (!"retraso".equalsIgnoreCase(orden)) return null;
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                var estado = root.get("estadoTiempo");
                var rango = cb.<Integer>selectCase()
                        .when(cb.equal(estado, com.polygraph.erp.shared.enums.EstadoTiempo.VENCIDA), 0)
                        .when(cb.equal(estado, com.polygraph.erp.shared.enums.EstadoTiempo.POR_VENCER), 1)
                        .when(cb.equal(estado, com.polygraph.erp.shared.enums.EstadoTiempo.A_TIEMPO), 2)
                        .when(cb.equal(estado, com.polygraph.erp.shared.enums.EstadoTiempo.EN_PAUSA), 3)
                        .otherwise(4);
                query.orderBy(cb.asc(rango), cb.desc(root.get("diasRetrasoHabiles")), cb.asc(root.get("fechaCompromisoAjustada")),
                        cb.desc(root.get("fechaSolicitud")));
            }
            return null;
        };
    }

    /**
     * El semáforo de estado como FILTRO (SE-04/SE-09): el servicio pertenece al estado si al menos uno de sus subprocesos está en él. Un servicio puede
     * estar en varios estados a la vez; uno sin subprocesos no está en ninguno. {@code estado == null}: sin filtro.
     */
    public static Specification<Servicio> conSubprocesoEn(EstadoSubproceso estado) {
        if (estado == null) return null;
        return (root, query, cb) -> {
            var sub = query.subquery(Integer.class);
            var sp = sub.from(ServicioSubproceso.class);
            sub.select(cb.literal(1)).where(cb.equal(sp.get("servicio"), root), cb.equal(sp.get("estado"), estado));
            return cb.exists(sub);
        };
    }

    /** PB-05: pertenece a alguno de los estados (p. ej. «Seguir la programación» = Programando + Reprogramado). Vacío = sin filtro. */
    public static Specification<Servicio> conSubprocesoEnAlguno(java.util.Collection<EstadoSubproceso> estados) {
        if (estados == null || estados.isEmpty()) return null;
        return (root, query, cb) -> {
            var sub = query.subquery(Integer.class);
            var sp = sub.from(ServicioSubproceso.class);
            sub.select(cb.literal(1)).where(cb.equal(sp.get("servicio"), root), sp.get("estado").in(estados));
            return cb.exists(sub);
        };
    }

    /** «ESTADO» o «ESTADO1,ESTADO2» → los estados; vacío = ninguno (sin filtro). */
    public static List<EstadoSubproceso> estadosDeFiltro(String texto) {
        if (texto == null || texto.isBlank()) return List.of();
        List<EstadoSubproceso> estados = new ArrayList<>();
        for (String parte : texto.split(",")) {
            EstadoSubproceso e = estadoDeFiltro(parte);
            if (e != null) estados.add(e);
        }
        return estados;
    }

    /** PB-05: el grupo de tiempo (Vencidas · Vence hoy · Vence mañana · A tiempo · En pausa); dice lo mismo que {@code BucketTiempo.de}. */
    public static Specification<Servicio> conBucket(com.polygraph.erp.shared.estados.BucketTiempo bucket) {
        if (bucket == null) return null;
        return (root, query, cb) -> {
            var tiempo = root.get("estadoTiempo");
            var dias = cb.coalesce(root.<Integer>get("diasRestantesHabiles"), 0);
            var porVencer = cb.equal(tiempo, com.polygraph.erp.shared.enums.EstadoTiempo.POR_VENCER);
            return switch (bucket) {
                case VENCIDAS -> cb.equal(tiempo, com.polygraph.erp.shared.enums.EstadoTiempo.VENCIDA);
                case EN_PAUSA -> cb.equal(tiempo, com.polygraph.erp.shared.enums.EstadoTiempo.EN_PAUSA);
                case VENCE_HOY -> cb.and(porVencer, cb.lessThanOrEqualTo(dias, 0));
                case VENCE_MANANA -> cb.and(porVencer, cb.equal(dias, 1));
                case A_TIEMPO -> cb.or(cb.equal(tiempo, com.polygraph.erp.shared.enums.EstadoTiempo.A_TIEMPO), cb.and(porVencer, cb.greaterThanOrEqualTo(dias, 2)));
            };
        };
    }

    /** PB-05: el estudio (proceso) de la solicitud. */
    public static Specification<Servicio> delProceso(Integer idProceso) {
        if (idProceso == null) return null;
        return (root, query, cb) -> cb.equal(root.get("proceso").get("idProceso"), idProceso);
    }

    /** PB-05: la persona responsable — ejecutor asignado o programador encargado de alguno de sus subprocesos. */
    public static Specification<Servicio> conResponsable(Long idResponsable) {
        if (idResponsable == null) return null;
        return (root, query, cb) -> {
            var sub = query.subquery(Integer.class);
            var sp = sub.from(ServicioSubproceso.class);
            sub.select(cb.literal(1)).where(cb.equal(sp.get("servicio"), root), cb.or(
                    cb.equal(sp.get("usuarioAsignado").get("idUsuario"), idResponsable),
                    cb.equal(sp.get("programadorResponsable").get("idUsuario"), idResponsable)));
            return cb.exists(sub);
        };
    }

    /** PB-05: estudios con la pasta emitida en el periodo (para «Mis solicitudes» filtrada por nivel y periodo) — null = sin límite. */
    public static Specification<Servicio> solicitadaDesde(java.time.LocalDate desde) {
        if (desde == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fechaSolicitud"), desde);
    }

    /** PB-05: el estudio tiene la pasta emitida y su nivel (el que vale: decidido o final) es el pedido. */
    public static Specification<Servicio> conNivelEmitido(com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo nivel) {
        if (nivel == null) return null;
        return (root, query, cb) -> {
            var sub = query.subquery(Long.class);
            var c = sub.from(com.polygraph.erp.modules.riesgo.entity.CalificacionRiesgo.class);
            sub.select(cb.literal(1L)).where(cb.equal(c.get("servicio"), root), cb.isTrue(c.get("vigente")),
                    cb.equal(cb.coalesce(c.get("nivelDecidido"), c.get("nivelFinal")), nivel));
            return cb.and(cb.equal(root.get("pastaEstado"), com.polygraph.erp.shared.enums.PastaEstado.EMITIDA), cb.exists(sub));
        };
    }

    /** Convierte el texto del filtro («EN_EJECUCION», «en_ejecucion»...) en el estado; vacío = sin filtro; un valor que no es uno de los siete, 400. */
    public static EstadoSubproceso estadoDeFiltro(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return EstadoSubproceso.valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.polygraph.erp.shared.exceptions.ApiException("Estado desconocido: " + texto, org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }


    /** Texto libre: nombre o cédula del candidato, cargo, proceso o número de solicitud. */
    public static Specification<Servicio> buscar(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String patron = "%" + texto.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            var candidato = root.join("candidato", JoinType.LEFT);
            var proceso = root.join("proceso", JoinType.LEFT);
            var cliente = root.join("cliente", JoinType.LEFT);
            List<Predicate> alguno = new ArrayList<>(List.of(
                    cb.like(cb.lower(cb.coalesce(cliente.<String>get("razonSocial"), "")), patron),
                    cb.like(cb.lower(cb.coalesce(cliente.<String>get("nombreComercial"), "")), patron),
                    cb.like(cb.lower(cb.concat(cb.concat(cb.coalesce(cliente.<String>get("nombre"), ""), " "), cb.coalesce(cliente.<String>get("apellido"), ""))), patron),
                    cb.like(cb.lower(candidato.get("nombres")), patron),
                    cb.like(cb.lower(candidato.get("apellidos")), patron),
                    cb.like(cb.lower(cb.concat(cb.concat(candidato.get("nombres"), " "), candidato.get("apellidos"))), patron),
                    cb.like(cb.lower(candidato.get("cedula")), patron),
                    cb.like(cb.lower(root.get("cargo")), patron),
                    cb.like(cb.lower(proceso.get("nombreProceso")), patron)));
            String numero = texto.trim().replace("#", "");
            if (numero.matches("\\d{1,9}")) {
                alguno.add(cb.equal(root.get("idServicio"), Integer.valueOf(numero)));
            }
            return cb.or(alguno.toArray(new Predicate[0]));
        };
    }
}
