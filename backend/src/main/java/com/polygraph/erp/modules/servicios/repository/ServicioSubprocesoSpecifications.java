package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.EstadoAsignacion;
import com.polygraph.erp.shared.enums.Rol;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ServicioSubprocesoSpecifications {

    private ServicioSubprocesoSpecifications() {}

    public static Specification<ServicioSubproceso> conFiltros(
            EstadoAsignacion estado, Integer idTipoProgreso, Long idUsuarioAsignado,
            LocalDateTime desde, LocalDateTime hasta) {
        return conFiltros(estado, idTipoProgreso, idUsuarioAsignado, desde, hasta, null, null);
    }

    /** @param idServicio limita a los subprocesos de un servicio puntual (usado para el despliegue en Solicitudes).
     *  @param idGestor    limita a servicios de clientes cuyo gestor asignado sea este usuario. */
    public static Specification<ServicioSubproceso> conFiltros(
            EstadoAsignacion estado, Integer idTipoProgreso, Long idUsuarioAsignado,
            LocalDateTime desde, LocalDateTime hasta, Integer idServicio, Long idGestor) {
        return conFiltros(estado, idTipoProgreso, idUsuarioAsignado, desde, hasta, idServicio, idGestor, null);
    }

    /** @param rolesPermitidos si no es null/vacío, limita a subprocesos cuyo tipo_progreso.rol_responsable
     *  esté en ese conjunto — usado para que PROGRAMADOR solo vea/actúe sobre poligrafía/visita. */
    public static Specification<ServicioSubproceso> conFiltros(
            EstadoAsignacion estado, Integer idTipoProgreso, Long idUsuarioAsignado,
            LocalDateTime desde, LocalDateTime hasta, Integer idServicio, Long idGestor, Set<Rol> rolesPermitidos) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();

            if (estado != null) {
                condiciones.add(cb.equal(root.get("estado"), estado));
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

            if (query != null) {
                query.orderBy(cb.desc(root.get("fechaCreacion")));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
    }
}
