package com.polygraph.erp.shared.enums;

/** Sugerencia de disponibilidad del candidato para una cita (V197). */
public enum EstadoSugerencia {
    PENDIENTE,
    /** El programador la vio al agendar. */
    ATENDIDA,
    /** El candidato mandó otra antes de que la atendieran: vale la más nueva. */
    REEMPLAZADA
}
