package com.polygraph.erp.shared.enums;

public enum EstadoAsignacion {
    PENDIENTE, ASIGNADO,
    /** Quien lo diligenció dice que ya terminó — queda pendiente de que GESTOR/PROGRAMADOR lo
     *  revise y lo apruebe (ver AsignacionService.aprobar) o lo devuelva (ver .devolver). */
    COMPLETADO,
    /** GESTOR/PROGRAMADOR ya revisó un COMPLETADO y lo aprobó — estado final de verdad, ya no
     *  cambia (aparte de CANCELADO, si aplicara). */
    PUBLICADO,
    CANCELADO
}
