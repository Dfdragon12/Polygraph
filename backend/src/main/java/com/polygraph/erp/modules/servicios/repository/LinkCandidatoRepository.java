package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.shared.enums.EstadoServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LinkCandidatoRepository extends JpaRepository<LinkCandidato, Long> {

    /** PB-04: enlaces enviados por Polygraph hace 24 h o más que el candidato no terminó y que aún no tienen su recordatorio. */
    @org.springframework.data.jpa.repository.Query("SELECT l FROM LinkCandidato l WHERE l.enviadoEn IS NOT NULL AND l.enviadoEn <= :limite AND l.ultimoRecordatorioEn IS NULL "
            + "AND l.revocado = false AND l.usado = false AND l.intentosFallidos < :maxIntentos AND l.servicio.estado NOT IN :cerrados")
    java.util.List<LinkCandidato> pendientesDeRecordatorio(java.time.LocalDateTime limite, int maxIntentos,
                                                           java.util.Collection<com.polygraph.erp.shared.enums.EstadoServicio> cerrados);

    /** «Reclama» el recordatorio de un enlace: solo uno gana, aunque corran dos procesos a la vez. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE LinkCandidato l SET l.ultimoRecordatorioEn = :ahora, l.recordatoriosEnviados = l.recordatoriosEnviados + 1 "
            + "WHERE l.id = :id AND l.ultimoRecordatorioEn IS NULL")
    int reclamarRecordatorio(Long id, java.time.LocalDateTime ahora);

    /** PB-05: los enlaces de varias solicitudes de una vez (la lista de Solicitudes dice cuáles ya se enviaron). */
    List<LinkCandidato> findByServicio_IdServicioIn(java.util.Collection<Integer> idsServicio);

    Optional<LinkCandidato> findByToken(String token);

    Optional<LinkCandidato> findTopByServicio_IdServicioOrderByFechaCreacionDesc(Integer idServicio);

    List<LinkCandidato> findByServicio_IdServicioOrderByFechaCreacionDesc(Integer idServicio);

    // ── Listas y conteos del gestor: SOLO links de servicios de sus clientes (Cliente.idGestor) ──
    // Por estado — mismas reglas que EstadoLink.de(...), en SQL (ver EstadoLink).
    // "vivo" = no revocado y no bloqueado; luego se separa por estado del servicio y por usado.

    String VIVO = "l.revocado = false AND (l.intentosFallidos IS NULL OR l.intentosFallidos < 3)";
    String DEL_GESTOR = "l.servicio.cliente.idGestor = :idGestor";

    @Query("SELECT l FROM LinkCandidato l WHERE " + DEL_GESTOR + " ORDER BY l.fechaCreacion DESC")
    Page<LinkCandidato> findDelGestor(@Param("idGestor") Long idGestor, Pageable pageable);

    @Query("SELECT l FROM LinkCandidato l WHERE " + DEL_GESTOR + " AND " + VIVO
            + " AND l.servicio.estado NOT IN :cerrados AND l.usado = :usado ORDER BY l.fechaCreacion DESC")
    Page<LinkCandidato> findConAcceso(@Param("idGestor") Long idGestor,
                                      @Param("cerrados") Collection<EstadoServicio> cerrados,
                                      @Param("usado") boolean usado, Pageable pageable);

    /** Pendientes (sin enviar) más antiguos primero — los que más llevan esperando al candidato. */
    @Query("SELECT l FROM LinkCandidato l WHERE " + DEL_GESTOR + " AND " + VIVO
            + " AND l.servicio.estado NOT IN :cerrados AND l.usado = false ORDER BY l.fechaCreacion ASC")
    List<LinkCandidato> findPendientesMasAntiguos(@Param("idGestor") Long idGestor,
                                                  @Param("cerrados") Collection<EstadoServicio> cerrados, Pageable pageable);

    @Query("SELECT l FROM LinkCandidato l WHERE " + DEL_GESTOR + " AND " + VIVO
            + " AND l.servicio.estado IN :cerrados ORDER BY l.fechaCreacion DESC")
    Page<LinkCandidato> findCerrados(@Param("idGestor") Long idGestor,
                                     @Param("cerrados") Collection<EstadoServicio> cerrados, Pageable pageable);

    @Query("SELECT l FROM LinkCandidato l WHERE " + DEL_GESTOR
            + " AND l.revocado = false AND l.intentosFallidos >= 3 ORDER BY l.fechaCreacion DESC")
    Page<LinkCandidato> findBloqueados(@Param("idGestor") Long idGestor, Pageable pageable);

    @Query("SELECT l FROM LinkCandidato l WHERE " + DEL_GESTOR + " AND l.revocado = true ORDER BY l.fechaCreacion DESC")
    Page<LinkCandidato> findRevocados(@Param("idGestor") Long idGestor, Pageable pageable);

    @Query("SELECT COUNT(l) FROM LinkCandidato l WHERE " + DEL_GESTOR)
    long contarDelGestor(@Param("idGestor") Long idGestor);

    @Query("SELECT COUNT(l) FROM LinkCandidato l WHERE " + DEL_GESTOR + " AND " + VIVO
            + " AND l.servicio.estado NOT IN :cerrados AND l.usado = :usado")
    long contarConAcceso(@Param("idGestor") Long idGestor,
                         @Param("cerrados") Collection<EstadoServicio> cerrados, @Param("usado") boolean usado);

    @Query("SELECT COUNT(l) FROM LinkCandidato l WHERE " + DEL_GESTOR + " AND " + VIVO + " AND l.servicio.estado IN :cerrados")
    long contarCerrados(@Param("idGestor") Long idGestor, @Param("cerrados") Collection<EstadoServicio> cerrados);

    @Query("SELECT COUNT(l) FROM LinkCandidato l WHERE " + DEL_GESTOR + " AND l.revocado = false AND l.intentosFallidos >= 3")
    long contarBloqueados(@Param("idGestor") Long idGestor);

    @Query("SELECT COUNT(l) FROM LinkCandidato l WHERE " + DEL_GESTOR + " AND l.revocado = true")
    long contarRevocados(@Param("idGestor") Long idGestor);
}
