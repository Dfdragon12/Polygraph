package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.SaldoServicioCliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SaldoServicioClienteRepository extends JpaRepository<SaldoServicioCliente, Long> {
    Optional<SaldoServicioCliente> findByCliente_IdClienteAndProceso_IdProceso(Integer idCliente, Integer idProceso);
    List<SaldoServicioCliente> findByCliente_IdCliente(Integer idCliente);
}
