package com.polygraph.erp.modules.tienda.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Vigencia de un lote (TP-07). Pura: sin Spring ni reloj. La fecha de compra ya llega en hora de Bogotá.
 * Vence al final (23:59:59) del día {@code fechaCompra + meses}; si ese mes no tiene el día, el último día del mes
 * (31 de enero + 3 meses = 30 de abril). Sin vigencia (null o 0), el lote no vence nunca.
 */
public final class CalculadoraVigencia {

    private static final LocalTime FIN_DEL_DIA = LocalTime.of(23, 59, 59);

    private CalculadoraVigencia() {}

    /** @return el instante de vencimiento, o null si el lote no vence. */
    public static LocalDateTime vencimiento(LocalDate fechaCompra, Integer vigenciaMeses) {
        if (vigenciaMeses == null || vigenciaMeses <= 0) return null;
        return LocalDateTime.of(fechaCompra.plusMonths(vigenciaMeses), FIN_DEL_DIA);   // plusMonths ya recorta al último día del mes
    }

    /** El último segundo de un día (23:59:59). */
    public static LocalDateTime finDelDia(LocalDate dia) {
        return LocalDateTime.of(dia, FIN_DEL_DIA);
    }

    /** ¿Ya venció? (el instante de vencimiento pasó). Un lote sin vencimiento nunca vence. */
    public static boolean vencido(LocalDateTime vencimiento, LocalDateTime ahora) {
        return vencimiento != null && ahora.isAfter(vencimiento);
    }
}
