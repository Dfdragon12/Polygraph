package com.polygraph.erp.shared.enums;

/** Motivo de una nota crédito o débito — categorías estándar usadas también por la DIAN para
 *  estos documentos, aunque acá todavía es solo un registro interno (ver NotaAjuste). */
public enum MotivoNotaAjuste {
    ANULACION,
    DEVOLUCION,
    DESCUENTO,
    AJUSTE_PRECIO,
    INTERESES_MORA,
    GASTOS_ADICIONALES,
    CORRECCION,
    OTRO
}
