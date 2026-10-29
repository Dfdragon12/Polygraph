package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.PagoWompi;
import com.polygraph.erp.shared.enums.EstadoPago;
import com.polygraph.erp.shared.enums.TipoConceptoPago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PagoWompiRepository extends JpaRepository<PagoWompi, UUID> {
    Optional<PagoWompi> findByReferencia(String referencia);

    /** Los intentos de un mismo pago comparten la base de la referencia (ver ReferenciaPago). */
    Optional<PagoWompi> findFirstByReferenciaStartingWith(String base);

    Optional<PagoWompi> findByTransaccionWompiId(String transaccionWompiId);

    List<PagoWompi> findByEstadoAndFechaCreacionBefore(EstadoPago estado, LocalDateTime fechaLimite);

    /** El pago PENDIENTE ya iniciado para este mismo concepto (si hay) — para reusar su referencia en un reintento, en vez de crear uno nuevo. */
    Optional<PagoWompi> findFirstByFacturaOConceptoIdAndTipoConceptoAndEstadoOrderByFechaCreacionDesc(
            Long facturaOConceptoId, TipoConceptoPago tipoConcepto, EstadoPago estado);

    /** Todos los intentos de pago hechos para este concepto — para mostrar la referencia real de Wompi y poder cruzarla con su panel. */
    List<PagoWompi> findByFacturaOConceptoIdAndTipoConceptoOrderByFechaCreacionDesc(Long facturaOConceptoId, TipoConceptoPago tipoConcepto);
}
