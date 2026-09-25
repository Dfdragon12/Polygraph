package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.NotaAjuste;
import com.polygraph.erp.shared.enums.TipoNotaAjuste;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface NotaAjusteRepository extends JpaRepository<NotaAjuste, Long> {
    List<NotaAjuste> findByOrdenCompra_IdOrdenCompraOrderByFechaGeneracionDesc(Long idOrdenCompra);

    List<NotaAjuste> findByFacturaPospago_IdFacturaOrderByFechaGeneracionDesc(Long idFactura);

    // Para no dejar emitir notas crédito que sumadas superen el valor del documento original —
    // ver NotaAjusteService.validarNoExcedeOriginal.
    @Query("select coalesce(sum(n.montoTotal), 0) from NotaAjuste n "
            + "where n.ordenCompra.idOrdenCompra = :idOrdenCompra and n.tipo = :tipo")
    BigDecimal sumMontoByOrdenCompraAndTipo(@Param("idOrdenCompra") Long idOrdenCompra, @Param("tipo") TipoNotaAjuste tipo);

    @Query("select coalesce(sum(n.montoTotal), 0) from NotaAjuste n "
            + "where n.facturaPospago.idFactura = :idFactura and n.tipo = :tipo")
    BigDecimal sumMontoByFacturaPospagoAndTipo(@Param("idFactura") Long idFactura, @Param("tipo") TipoNotaAjuste tipo);
}
