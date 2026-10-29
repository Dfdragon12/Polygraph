package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.SugerenciaDisponibilidad;
import com.polygraph.erp.shared.enums.EstadoSugerencia;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SugerenciaDisponibilidadRepository extends JpaRepository<SugerenciaDisponibilidad, Long> {

    @EntityGraph(attributePaths = "opciones")
    Optional<SugerenciaDisponibilidad> findFirstByServicioSubproceso_IdAndEstadoOrderByFechaCreacionDesc(
            Long idServicioSubproceso, EstadoSugerencia estado);

    /** Subprocesos con sugerencia pendiente que le tocan a este actor — para marcarlos en la
     *  agenda. todos=true (admin) ve todos; si no, los que coordina como programador responsable
     *  o los de los clientes que gestiona. */
    @Query("""
            SELECT DISTINCT s.servicioSubproceso.id FROM SugerenciaDisponibilidad s
            WHERE s.estado = com.polygraph.erp.shared.enums.EstadoSugerencia.PENDIENTE
              AND (:todos = true
                   OR s.servicioSubproceso.programadorResponsable.idUsuario = :idUsuario
                   OR s.servicioSubproceso.servicio.cliente.idGestor = :idUsuario)
            """)
    List<Long> idsSubprocesoConPendiente(@Param("todos") boolean todos, @Param("idUsuario") Long idUsuario);

    /** Las pendientes de varios subprocesos de una vez (portal del candidato: una por cita). */
    @EntityGraph(attributePaths = {"opciones", "servicioSubproceso"})
    List<SugerenciaDisponibilidad> findByServicioSubproceso_IdInAndEstado(Collection<Long> ids, EstadoSugerencia estado);
}
