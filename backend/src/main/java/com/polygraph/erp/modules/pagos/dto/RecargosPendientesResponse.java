package com.polygraph.erp.modules.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Recargos por ciudad acumulados de solicitudes anteriores, todavía sin cobrar — se suman a la siguiente compra. */
public record RecargosPendientesResponse(
        BigDecimal montoTotal,
        List<Item> items
) {
    public record Item(
            Integer idRecargoPendiente,
            String nombreProceso,
            String nombreCandidato,
            String nombreCiudad,
            BigDecimal monto,
            String motivo,
            LocalDateTime fechaGenerado
    ) {}
}
