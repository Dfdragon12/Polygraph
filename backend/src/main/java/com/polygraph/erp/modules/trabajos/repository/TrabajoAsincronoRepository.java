package com.polygraph.erp.modules.trabajos.repository;

import com.polygraph.erp.modules.trabajos.entity.TrabajoAsincrono;
import com.polygraph.erp.shared.enums.EstadoTrabajo;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** La toma de trabajos de la cola (FOR UPDATE SKIP LOCKED) no está aquí: es SQL nativo con
 *  RETURNING y vive en DespachadorTrabajos, con JdbcTemplate. */
public interface TrabajoAsincronoRepository extends JpaRepository<TrabajoAsincrono, Long> {

    @EntityGraph(attributePaths = "usuario")
    Optional<TrabajoAsincrono> findByUuid(UUID uuid);

    boolean existsByTipoAndEstadoIn(TipoTrabajo tipo, java.util.Collection<EstadoTrabajo> estados);

    /** Con el usuario ya cargado: el procesador corre fuera de transacción y lo necesita. */
    @EntityGraph(attributePaths = "usuario")
    Optional<TrabajoAsincrono> findConUsuarioById(Long id);

    @EntityGraph(attributePaths = "usuario")
    @Query("""
            SELECT t FROM TrabajoAsincrono t
            WHERE (:estado IS NULL OR t.estado = :estado)
              AND (:tipo IS NULL OR t.tipo = :tipo)
            """)
    Page<TrabajoAsincrono> buscar(@Param("estado") EstadoTrabajo estado,
                                  @Param("tipo") TipoTrabajo tipo,
                                  Pageable pageable);

    /** [tipo, estado, cantidad] — para los contadores del panel. */
    @Query("SELECT t.tipo, t.estado, COUNT(t) FROM TrabajoAsincrono t GROUP BY t.tipo, t.estado")
    List<Object[]> contarPorTipoYEstado();

    long countByEstadoAndFechaFinAfter(EstadoTrabajo estado, LocalDateTime desde);

    /** Cuántos del mismo tipo están antes en la fila — la "posición" que ve el usuario. */
    long countByTipoAndEstadoAndIdLessThan(TipoTrabajo tipo, EstadoTrabajo estado, Long id);

    @Modifying
    @Transactional
    @Query("UPDATE TrabajoAsincrono t SET t.progreso = :progreso WHERE t.id = :id AND t.estado = com.polygraph.erp.shared.enums.EstadoTrabajo.PROCESANDO")
    void actualizarProgreso(@Param("id") Long id, @Param("progreso") int progreso);

    /** Trabajos que quedaron en PROCESANDO más de lo razonable (el backend se reinició o el hilo
     *  murió en medio) — LimpiezaTrabajosJob los devuelve a la cola o los da por fallidos. */
    List<TrabajoAsincrono> findByTipoAndEstadoAndFechaInicioBefore(TipoTrabajo tipo, EstadoTrabajo estado, LocalDateTime limite);

    /** Por lotes, para no cargar miles de filas en memoria de una sola vez al limpiar. */
    List<TrabajoAsincrono> findTop200ByEstadoAndFechaFinBeforeOrderByIdAsc(EstadoTrabajo estado, LocalDateTime limite);
}
