package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LinkCandidatoRepository extends JpaRepository<LinkCandidato, Long> {

    Optional<LinkCandidato> findByToken(String token);

    Optional<LinkCandidato> findTopByServicio_IdServicioOrderByFechaCreacionDesc(Integer idServicio);

    List<LinkCandidato> findByServicio_IdServicioOrderByFechaCreacionDesc(Integer idServicio);

    Page<LinkCandidato> findAllByOrderByFechaCreacionDesc(Pageable pageable);

    @Query("SELECT l FROM LinkCandidato l WHERE l.usado = false AND l.fechaExpiracion > :ahora AND (l.intentosFallidos IS NULL OR l.intentosFallidos < 3) ORDER BY l.fechaCreacion DESC")
    Page<LinkCandidato> findPendientes(@Param("ahora") LocalDateTime ahora, Pageable pageable);

    @Query("SELECT l FROM LinkCandidato l WHERE l.usado = false AND l.fechaExpiracion > :ahora AND (l.intentosFallidos IS NULL OR l.intentosFallidos < 3) ORDER BY l.fechaExpiracion ASC")
    List<LinkCandidato> findPendientesOrderByExpiracionAsc(@Param("ahora") LocalDateTime ahora, Pageable pageable);

    @Query("SELECT l FROM LinkCandidato l WHERE l.usado = true ORDER BY l.fechaCreacion DESC")
    Page<LinkCandidato> findUsados(Pageable pageable);

    @Query("SELECT l FROM LinkCandidato l WHERE l.usado = false AND l.fechaExpiracion <= :ahora ORDER BY l.fechaCreacion DESC")
    Page<LinkCandidato> findExpirados(@Param("ahora") LocalDateTime ahora, Pageable pageable);

    @Query("SELECT l FROM LinkCandidato l WHERE l.usado = false AND l.intentosFallidos >= 3 ORDER BY l.fechaCreacion DESC")
    Page<LinkCandidato> findBloqueados(Pageable pageable);

    long countByUsadoTrue();

    long countByUsadoFalseAndFechaExpiracionAfterAndIntentosFallidosLessThan(
            LocalDateTime ahora, Short limite);

    long countByUsadoFalseAndFechaExpiracionBefore(LocalDateTime ahora);

    long countByUsadoFalseAndIntentosFallidosGreaterThanEqual(Short limite);

    // Validación: ¿existe ya un token activo para este servicio?
    boolean existsByServicio_IdServicioAndUsadoFalseAndFechaExpiracionAfterAndIntentosFallidosLessThan(
            Integer idServicio, LocalDateTime ahora, Short limite);

    // Validación: ¿existe un token activo en alguno de estos servicios? (misma cédula)
    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN TRUE ELSE FALSE END FROM LinkCandidato l " +
           "WHERE l.servicio.idServicio IN :ids " +
           "AND l.usado = false " +
           "AND l.fechaExpiracion > :ahora " +
           "AND (l.intentosFallidos IS NULL OR l.intentosFallidos < :limite)")
    boolean existsActivoEnServicios(
            @Param("ids")    List<Integer>   ids,
            @Param("ahora")  LocalDateTime   ahora,
            @Param("limite") Short           limite);
}
