package com.polygraph.erp.modules.pagos.dto;

import java.math.BigDecimal;

/** Sumas prepago para la barra "Pagado / Pendiente / Vencido" de Facturacion.jsx — prepago no
 *  tiene noción de "vencido" (una orden simplemente expira sin pagarse), por eso solo trae estos
 *  dos; el frontend lo combina con ResumenFacturasPospagoResponse. */
public record ResumenPagosOrdenesResponse(
        BigDecimal pagado,
        BigDecimal pendiente
) {}
