package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.ReversionSolicitud;
import com.polygraph.erp.shared.enums.EstadoAprobacion;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ReversionSolicitudSpecifications {

    private ReversionSolicitudSpecifications() {}

    public static Specification<ReversionSolicitud> conFiltros(EstadoAprobacion estado, String q) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();

            if (estado != null) {
                condiciones.add(cb.equal(root.get("estado"), estado));
            }

            if (q != null && !q.isBlank()) {
                var servicio  = root.join("servicio", JoinType.INNER);
                var candidato = servicio.join("candidato", JoinType.LEFT);
                var cliente   = servicio.join("cliente", JoinType.LEFT);

                String patron = "%" + q.toLowerCase() + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(candidato.get("nombres")), patron),
                        cb.like(cb.lower(candidato.get("apellidos")), patron),
                        cb.like(cb.lower(candidato.get("cedula")), patron),
                        cb.like(cb.lower(cliente.get("nombre")), patron),
                        cb.like(cb.lower(cliente.get("apellido")), patron),
                        cb.like(cb.lower(cliente.get("razonSocial")), patron),
                        cb.like(cb.lower(root.get("motivo")), patron)
                ));
            }

            if (query != null) {
                query.orderBy(cb.desc(root.get("fechaSolicitud")));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
    }
}
