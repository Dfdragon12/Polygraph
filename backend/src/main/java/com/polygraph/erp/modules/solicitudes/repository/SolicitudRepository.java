package com.polygraph.erp.modules.solicitudes.repository;

import com.polygraph.erp.modules.solicitudes.entity.Solicitud;
import com.polygraph.erp.shared.enums.EstadoServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {

    Page<Solicitud> findByCliente_IdClienteOrderByFechaSolicitudDesc(Integer idCliente, Pageable pageable);

    Page<Solicitud> findByCliente_IdClienteAndEstadoOrderByFechaSolicitudDesc(
            Integer idCliente, EstadoServicio estado, Pageable pageable);

    // Para el módulo Gestor: visibilidad global
    Page<Solicitud> findAllByOrderByFechaSolicitudDesc(Pageable pageable);

    Page<Solicitud> findByEstadoOrderByFechaSolicitudDesc(EstadoServicio estado, Pageable pageable);

    long countByEstado(EstadoServicio estado);

    List<Solicitud> findTop8ByOrderByFechaSolicitudDesc();
}
