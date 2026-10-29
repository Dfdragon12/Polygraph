package com.polygraph.erp.modules.riesgo.repository;

import com.polygraph.erp.modules.riesgo.entity.CalificacionRiesgo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CalificacionRiesgoRepository extends JpaRepository<CalificacionRiesgo, Long> {

    @EntityGraph(attributePaths = {"calculadoPor", "decididoPor"})
    Optional<CalificacionRiesgo> findFirstByServicio_IdServicioAndVigenteTrue(Integer idServicio);

    @EntityGraph(attributePaths = {"calculadoPor", "decididoPor"})
    List<CalificacionRiesgo> findByServicio_IdServicioOrderByFechaCalculoDescIdDesc(Integer idServicio);

    /** Los cálculos vigentes de un estado que todavía no están bloqueados (SE-06: revisar los que quedaron «CALCULADO» con puntaje 0). */
    @EntityGraph(attributePaths = {"servicio"})
    List<CalificacionRiesgo> findByVigenteTrueAndEstadoAndBloqueadoFalse(com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.EstadoResultado estado);
}
