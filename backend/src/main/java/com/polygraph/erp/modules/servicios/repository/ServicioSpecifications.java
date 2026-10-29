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
        if ("retraso".equalsIgnoreCase(orden)) {
            return org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("diasRetrasoHabiles").nullsLast(),
                    org.springframework.data.domain.Sort.Order.asc("fechaCompromisoAjustada").nullsLast(),
                    org.springframework.data.domain.Sort.Order.desc("fechaSolicitud"));
        }
        return org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "fechaSolicitud", "horaSolicitud");
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
            List<Predicate> alguno = new ArrayList<>(List.of(
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
