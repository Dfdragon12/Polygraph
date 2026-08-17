package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.PagoWompi;
import com.polygraph.erp.shared.enums.EstadoPago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PagoWompiRepository extends JpaRepository<PagoWompi, UUID> {
    Optional<PagoWompi> findByReferencia(String referencia);

    Optional<PagoWompi> findByTransaccionWompiId(String transaccionWompiId);

    List<PagoWompi> findByEstadoAndFechaCreacionBefore(EstadoPago estado, LocalDateTime fechaLimite);
}
