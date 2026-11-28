package com.polygraph.erp.modules.pagos.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cotización de un carrito (TP-03): lo que se cobraría hoy, calculado por el backend con el mismo código con que se crea la orden.
 * Subtotal + IVA = Total, y el total es lo que se envía a la pasarela. React solo lo muestra.
 */
public record CotizacionOrdenResponse(
        List<Linea> lineas,
        BigDecimal recargosSubtotal,
        BigDecimal recargosIva,
        BigDecimal subtotal,
        BigDecimal iva,
        BigDecimal total,
        BigDecimal ivaPorcentaje,
        boolean cobroConIva
) {
    public record Linea(
            Integer idProceso,
            String nombreProceso,
            Integer unidadesPlan,
            Integer cantidadPaquetes,
            Integer cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal,
            BigDecimal iva,
            BigDecimal total
    ) {}
}
