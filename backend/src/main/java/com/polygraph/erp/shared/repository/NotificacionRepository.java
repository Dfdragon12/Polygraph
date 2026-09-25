package com.polygraph.erp.shared.repository;

import com.polygraph.erp.shared.entity.Notificacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    @EntityGraph(attributePaths = "realizadoPor")
    List<Notificacion> findTop15ByUsuario_EmailOrderByFechaCreacionDesc(String email);

    long countByUsuario_EmailAndLeidaFalse(String email);

    Optional<Notificacion> findByIdAndUsuario_Email(Long id, String email);

    /** Listado completo paginado para la campana ("Cargar más") — con y sin filtro de no leídas. */
    @EntityGraph(attributePaths = "realizadoPor")
    Page<Notificacion> findByUsuario_EmailOrderByFechaCreacionDescIdDesc(String email, Pageable pageable);

    @EntityGraph(attributePaths = "realizadoPor")
    Page<Notificacion> findByUsuario_EmailAndLeidaFalseOrderByFechaCreacionDescIdDesc(String email, Pageable pageable);

    /** Una notificación todavía sin leer del mismo tipo sobre el mismo registro — se reutiliza en
     *  vez de crear otra (ver NotificacionService.crearOAgruparParaUsuario). */
    Optional<Notificacion> findFirstByUsuario_IdUsuarioAndTipoAndReferenciaTipoAndReferenciaIdAndLeidaFalseOrderByFechaCreacionDesc(
            Long idUsuario, String tipo, String referenciaTipo, Long referenciaId);

    @Modifying
    @Query("DELETE FROM Notificacion n WHERE n.usuario.email = :email AND n.leida = true")
    int eliminarLeidas(String email);

    @Modifying
    @Query("UPDATE Notificacion n SET n.leida = true, n.fechaLectura = :ahora WHERE n.usuario.email = :email AND n.leida = false")
    void marcarTodasLeidas(String email, LocalDateTime ahora);
}
