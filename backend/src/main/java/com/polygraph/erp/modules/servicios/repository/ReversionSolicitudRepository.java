package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.ReversionSolicitud;
import com.polygraph.erp.shared.enums.EstadoAprobacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReversionSolicitudRepository extends JpaRepository<ReversionSolicitud, Long>,
        JpaSpecificationExecutor<ReversionSolicitud> {

    boolean existsByServicio_IdServicioAndEstado(Integer idServicio, EstadoAprobacion estado);

    long countByEstado(EstadoAprobacion estado);
}
