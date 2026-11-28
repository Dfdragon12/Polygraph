package com.polygraph.erp.shared.enums;

/** Quién originó una pausa del reloj (la demora no es de Polygraph). */
public enum OrigenPausa {
    CANDIDATO,
    CLIENTE,
    /** Mientras algún subproceso del servicio esté en REPROGRAMADO (cita no realizada) el reloj se pausa, sea cual sea el motivo. */
    REPROGRAMACION
}
