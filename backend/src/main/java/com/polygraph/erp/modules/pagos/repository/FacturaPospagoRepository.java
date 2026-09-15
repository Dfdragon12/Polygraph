package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.FacturaPospago;
import com.polygraph.erp.shared.enums.EstadoFacturaPospago;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FacturaPospagoRepository extends JpaRepository<FacturaPospago, Long> {
    List<FacturaPospago> findByCliente_IdClienteOrderByFechaGeneracionDesc(Integer idCliente);

    Optional<FacturaPospago> findByReferencia(String referencia);

    Page<FacturaPospago> findByEstadoOrderByFechaGeneracionDesc(EstadoFacturaPospago estado, Pageable pageable);

    Page<FacturaPospago> findAllByOrderByFechaGeneracionDesc(Pageable pageable);

    List<FacturaPospago> findByEstadoAndFechaVencimientoBefore(EstadoFacturaPospago estado, LocalDate fecha);
}
