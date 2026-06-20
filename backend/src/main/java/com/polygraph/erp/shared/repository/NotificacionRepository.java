package com.polygraph.erp.shared.repository;

import com.polygraph.erp.shared.entity.Notificacion;
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

    @Modifying
    @Query("UPDATE Notificacion n SET n.leida = true, n.fechaLectura = :ahora WHERE n.usuario.email = :email AND n.leida = false")
    void marcarTodasLeidas(String email, LocalDateTime ahora);
}
