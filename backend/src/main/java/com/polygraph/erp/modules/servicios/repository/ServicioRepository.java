package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.shared.enums.EstadoServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ServicioRepository extends JpaRepository<Servicio, Integer> {

    long countByEstado(EstadoServicio estado);
    long countByCliente_IdClienteAndEstado(Integer idCliente, EstadoServicio estado);

    long countByCliente_IdClienteAndEstadoIn(Integer idCliente, List<EstadoServicio> estados);

    long countByCliente_IdClienteAndEstadoInAndFechaSolicitudBetween(
            Integer idCliente, List<EstadoServicio> estados, LocalDate inicio, LocalDate fin);

    Page<Servicio> findByCliente_IdClienteOrderByFechaSolicitudDescHoraSolicitudDesc(
            Integer idCliente, Pageable pageable);

    Page<Servicio> findByCliente_IdClienteAndEstadoOrderByFechaSolicitudDescHoraSolicitudDesc(
            Integer idCliente, EstadoServicio estado, Pageable pageable);
}
