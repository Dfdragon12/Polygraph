package com.polygraph.erp.modules.clientes.repository;

import com.polygraph.erp.modules.clientes.entity.SoporteCliente;
import com.polygraph.erp.shared.enums.EstadoSoporte;
import com.polygraph.erp.shared.enums.TipoSoporte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SoporteClienteRepository extends JpaRepository<SoporteCliente, Integer> {
    List<SoporteCliente> findByCliente_IdCliente(Integer idCliente);
    Optional<SoporteCliente> findByCliente_IdClienteAndTipoSoporte(Integer idCliente, TipoSoporte tipoSoporte);
    List<SoporteCliente> findByEstadoAndFechaVencimientoIsNotNull(EstadoSoporte estado);
    long countByCliente_IdClienteAndEstado(Integer idCliente, EstadoSoporte estado);
}
