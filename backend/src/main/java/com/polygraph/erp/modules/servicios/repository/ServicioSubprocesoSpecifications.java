package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.evaluados.entity.DocumentoEvaluado;
import com.polygraph.erp.modules.servicios.entity.NovedadSubproceso;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.EstadoNovedad;
import com.polygraph.erp.shared.enums.EstadoRevisionDocumento;
import java.time.LocalDate;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ServicioSubprocesoSpecifications {

    private ServicioSubprocesoSpecifications() {}

    /** Buscador de las tablas de asignaciones: nombre o apellido del evaluado, cédula, cargo,
     *  subproceso, proceso, o el número del servicio (si se escribe un número). Sin distinguir
     *  mayúsculas. null/vacío = no filtra. */
    public static Specification<ServicioSubproceso> buscar(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String patron = "%" + texto.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            var servicio = root.join("servicio", JoinType.LEFT);
            var candidato = servicio.join("candidato", JoinType.LEFT);
            var proceso = servicio.join("proceso", JoinType.LEFT);
            var tipo = root.join("tipoProgreso", JoinType.LEFT);
            List<Predicate> alguno = new ArrayList<>(List.of(
                    cb.like(cb.lower(candidato.get("nombres")), patron),
                    cb.like(cb.lower(candidato.get("apellidos")), patron),
                    cb.like(cb.lower(cb.concat(cb.concat(candidato.get("nombres"), " "), candidato.get("apellidos"))), patron),
                    cb.like(cb.lower(candidato.get("cedula")), patron),
                    cb.like(cb.lower(servicio.get("cargo")), patron),
                    cb.like(cb.lower(tipo.get("nombreProgreso")), patron),
                    cb.like(cb.lower(proceso.get("nombreProceso")), patron)));
            String numero = texto.trim().replace("#", "");
            if (numero.matches("\\d{1,9}")) {
                alguno.add(cb.equal(servicio.get("idServicio"), Integer.valueOf(numero)));
            }
            return cb.or(alguno.toArray(new Predicate[0]));
        };
    }

    public static final String IND_POR_REVISAR = "revisar";
    public static final String IND_DEVUELTOS = "devueltos";
    public static final String IND_NOVEDADES = "novedades";
    public static final String IND_POR_VENCER = "urgentes";
    /** Entrega vencida o en los próximos días: umbral de "vencidas o por vencer". */

    private static final List<EstadoRevisionDocumento> ESTADOS_DEVUELTOS = List.of(
            EstadoRevisionDocumento.DENEGADO, EstadoRevisionDocumento.ILEGIBLE, EstadoRevisionDocumento.VENCIDO,
            EstadoRevisionDocumento.INCOMPLETO, EstadoRevisionDocumento.NO_CORRESPONDE);

    /** Filtro por indicador de "Por hacer" de quien ejecuta: documentos del candidato por revisar o
     *  devueltos, novedades propias sin respuesta (de idUsuario), o entrega vencida / por vencer.
     *  null o desconocido = no filtra. */
    public static Specification<ServicioSubproceso> conIndicador(String indicador, Long idUsuario) {
        if (indicador == null || indicador.isBlank()) return null;
        return switch (indicador) {
            case IND_POR_REVISAR -> documentosDelCandidato(true);
            case IND_DEVUELTOS -> documentosDelCandidato(false);
            case IND_NOVEDADES -> (root, query, cb) -> {
                var sq = query.subquery(Long.class);
                var n = sq.from(NovedadSubproceso.class);
                sq.select(n.get("id")).where(
                        cb.equal(n.get("servicioSubproceso"), root),
                        cb.equal(n.get("registradoPor").get("idUsuario"), idUsuario),
                        cb.equal(n.get("estado"), EstadoNovedad.ABIERTA));
                return cb.exists(sq);
            };
            // Urgentes = el estudio está vencido o por vencer, según el estado de tiempo guardado (días hábiles).
            case IND_POR_VENCER -> (root, query, cb) -> root.get("servicio").get("estadoTiempo").in(
                    com.polygraph.erp.shared.enums.EstadoTiempo.VENCIDA, com.polygraph.erp.shared.enums.EstadoTiempo.POR_VENCER);
            default -> null;
        };
    }

    private static Specification<ServicioSubproceso> documentosDelCandidato(boolean porRevisar) {
        return (root, query, cb) -> {
            var sq = query.subquery(Long.class);
            var d = sq.from(DocumentoEvaluado.class);
            var mismoCandidato = cb.equal(d.get("hojaVidaEvaluado").get("candidato"), root.get("servicio").get("candidato"));
            var estado = porRevisar
                    ? cb.or(cb.isNull(d.get("estadoRevision")), cb.equal(d.get("estadoRevision"), EstadoRevisionDocumento.PENDIENTE))
                    : d.get("estadoRevision").in(ESTADOS_DEVUELTOS);
            sq.select(d.get("id")).where(mismoCandidato, estado);
            return cb.exists(sq);
        };
    }

    public static Specification<ServicioSubproceso> conFiltros(
            EstadoSubproceso estado, Integer idTipoProgreso, Long idUsuarioAsignado,
            LocalDateTime desde, LocalDateTime hasta) {
        return conFiltros(estado, idTipoProgreso, idUsuarioAsignado, desde, hasta, null, null);
    }

    /** @param idServicio limita a los subprocesos de un servicio puntual (usado para el despliegue en Solicitudes).
     *  @param idGestor    limita a servicios de clientes cuyo gestor asignado sea este usuario. */
    public static Specification<ServicioSubproceso> conFiltros(
            EstadoSubproceso estado, Integer idTipoProgreso, Long idUsuarioAsignado,
            LocalDateTime desde, LocalDateTime hasta, Integer idServicio, Long idGestor) {
        return conFiltros(estado, idTipoProgreso, idUsuarioAsignado, desde, hasta, idServicio, idGestor, null);
    }

    /** @param rolesPermitidos si no es null/vacío, limita a subprocesos cuyo tipo_progreso.rol_responsable
     *  esté en ese conjunto — usado para que PROGRAMADOR solo vea/actúe sobre poligrafía/visita. */
    public static Specification<ServicioSubproceso> conFiltros(
            EstadoSubproceso estado, Integer idTipoProgreso, Long idUsuarioAsignado,
            LocalDateTime desde, LocalDateTime hasta, Integer idServicio, Long idGestor, Set<Rol> rolesPermitidos) {
        return conFiltros(estado, idTipoProgreso, idUsuarioAsignado, desde, hasta, idServicio, idGestor, rolesPermitidos, null);
    }

    /** @param idProgramadorResponsable filtro "solo lo mío" de PROGRAMADOR — a qué programador le
     *  encargó el gestor coordinar el subproceso (ver ServicioSubproceso.programadorResponsable). */
    public static Specification<ServicioSubproceso> conFiltros(
            EstadoSubproceso estado, Integer idTipoProgreso, Long idUsuarioAsignado,
            LocalDateTime desde, LocalDateTime hasta, Integer idServicio, Long idGestor, Set<Rol> rolesPermitidos,
            Long idProgramadorResponsable) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();

            if (estado != null) {
                // Mientras no existan los filtros por los siete estados (SE-04), "Pendiente" sigue incluyendo lo que espera agenda.
                condiciones.add(estado == EstadoSubproceso.PENDIENTE
                        ? root.get("estado").in(EstadoSubproceso.PENDIENTE, EstadoSubproceso.PROGRAMANDO)
                        : cb.equal(root.get("estado"), estado));
            }
            if (idTipoProgreso != null) {
                condiciones.add(cb.equal(root.get("tipoProgreso").get("idTipoProgreso"), idTipoProgreso));
            }
            if (idUsuarioAsignado != null) {
                condiciones.add(cb.equal(root.get("usuarioAsignado").get("idUsuario"), idUsuarioAsignado));
            }
            if (desde != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("fechaProgramada"), desde));
            }
            if (hasta != null) {
                condiciones.add(cb.lessThanOrEqualTo(root.get("fechaProgramada"), hasta));
            }
            if (idServicio != null) {
                condiciones.add(cb.equal(root.get("servicio").get("idServicio"), idServicio));
            }
            if (idGestor != null) {
                condiciones.add(cb.equal(root.get("servicio").get("cliente").get("idGestor"), idGestor));
            }
            if (rolesPermitidos != null && !rolesPermitidos.isEmpty()) {
                condiciones.add(root.get("tipoProgreso").get("rolResponsable").in(rolesPermitidos));
            }
            if (idProgramadorResponsable != null) {
                condiciones.add(cb.equal(root.get("programadorResponsable").get("idUsuario"), idProgramadorResponsable));
            }

            if (query != null) {
                query.orderBy(cb.desc(root.get("fechaCreacion")));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
    }
}
