package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.RecargoPendiente;
import com.polygraph.erp.shared.enums.EstadoRecargoPendiente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecargoPendienteRepository extends JpaRepository<RecargoPendiente, Integer> {
    List<RecargoPendiente> findByCliente_IdClienteAndEstadoOrderByFechaGeneradoAsc(Integer idCliente, EstadoRecargoPendiente estado);

    Optional<RecargoPendiente> findTopByCandidato_IdCandidatoAndProceso_IdProcesoOrderByFechaGeneradoDesc(Long idCandidato, Integer idProceso);

    List<RecargoPendiente> findByCandidato_IdCandidatoAndProceso_IdProceso(Long idCandidato, Integer idProceso);

    List<RecargoPendiente> findByCandidato_IdCandidatoAndProceso_IdProcesoOrderByFechaGeneradoAsc(Long idCandidato, Integer idProceso);
}
