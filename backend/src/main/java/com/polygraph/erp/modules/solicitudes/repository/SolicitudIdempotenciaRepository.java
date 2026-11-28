package com.polygraph.erp.modules.solicitudes.repository;

import com.polygraph.erp.modules.solicitudes.entity.SolicitudIdempotencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SolicitudIdempotenciaRepository extends JpaRepository<SolicitudIdempotencia, Long> {
    Optional<SolicitudIdempotencia> findByIdUsuarioAndClave(Long idUsuario, String clave);
}
