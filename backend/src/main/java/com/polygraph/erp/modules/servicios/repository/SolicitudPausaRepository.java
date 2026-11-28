package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.SolicitudPausa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SolicitudPausaRepository extends JpaRepository<SolicitudPausa, Long> {
    List<SolicitudPausa> findByServicio_IdServicioOrderByInicio(Integer idServicio);

    Optional<SolicitudPausa> findFirstByServicio_IdServicioAndFinIsNull(Integer idServicio);
}
