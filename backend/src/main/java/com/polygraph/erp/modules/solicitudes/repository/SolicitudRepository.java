package com.polygraph.erp.modules.solicitudes.repository;

import com.polygraph.erp.modules.solicitudes.entity.Solicitud;
import com.polygraph.erp.shared.enums.EstadoServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {

    Page<Solicitud> findByCliente_IdClienteOrderByFechaSolicitudDesc(Integer idCliente, Pageable pageable);

    Page<Solicitud> findByCliente_IdClienteAndEstadoOrderByFechaSolicitudDesc(
            Integer idCliente, EstadoServicio estado, Pageable pageable);

    long countByCliente_IdClienteAndEstado(Integer idCliente, EstadoServicio estado);

    long countByCliente_IdClienteAndEstadoIn(Integer idCliente, List<EstadoServicio> estados);

    long countByCliente_IdClienteAndEstadoInAndFechaSolicitudBetween(
            Integer idCliente, List<EstadoServicio> estados,
            LocalDateTime inicio, LocalDateTime fin);
}
