package com.polygraph.erp.modules.pagos.dto;

import java.math.BigDecimal;

/** Sumas pospago para la barra "Pagado / Pendiente / Vencido" de Facturacion.jsx — ver
 *  ResumenPagosOrdenesResponse para la mitad prepago. */
public record ResumenFacturasPospagoResponse(
        BigDecimal pagado,
        BigDecimal pendiente,
        BigDecimal vencido
) {}
