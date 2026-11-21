package com.polygraph.erp.modules.pagos.dto;

import java.math.BigDecimal;

public record ItemOrdenResponse(
        Integer idProceso,
        String nombreProceso,
        Integer cantidad,
        BigDecimal valorOriginal,
        BigDecimal valorUnitario,
        BigDecimal subtotal,
        String nombreDescuentoAplicado,
        Integer unidadesPlan,
        Integer cantidadPaquetes,
        String cobertura,
        BigDecimal iva,
        /** TP-07: vigencia en meses de las unidades compradas (null = no vencen o el interruptor estaba apagado). */
        Integer vigenciaMeses,
        /** TP-09: día en que vencen las unidades de esta compra (null si no vencen o la compra no está aprobada). */
        java.time.LocalDate fechaVencimiento
) {}
