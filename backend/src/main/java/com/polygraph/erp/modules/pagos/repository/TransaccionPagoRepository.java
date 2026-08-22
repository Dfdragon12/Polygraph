package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.TransaccionPago;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransaccionPagoRepository extends JpaRepository<TransaccionPago, Long> {
    boolean existsByIdTransaccionWompi(String idTransaccionWompi);
}
