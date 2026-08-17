package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.AuditoriaPago;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditoriaPagoRepository extends JpaRepository<AuditoriaPago, UUID> {
    Page<AuditoriaPago> findByReferenciaOrderByFechaEventoDesc(String referencia, Pageable pageable);

    Page<AuditoriaPago> findAllByOrderByFechaEventoDesc(Pageable pageable);
}
