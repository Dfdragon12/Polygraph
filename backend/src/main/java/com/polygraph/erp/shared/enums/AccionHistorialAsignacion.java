package com.polygraph.erp.shared.enums;

public enum AccionHistorialAsignacion {
    ASIGNADO,
    REASIGNADO,
    DESASIGNADO,
    COMPLETADO,
    CANCELADO,
    DEVUELTO,
    PUBLICADO,
    /** La hora del subproceso se corrió sin cambiar de responsable — p.ej. al resolver una
     *  novedad (ver AsignacionService.reprogramar). */
    REPROGRAMADO
}
