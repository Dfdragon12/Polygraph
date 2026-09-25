package com.polygraph.erp.shared.enums;

/** Estado de una NovedadSubproceso — ABIERTA hasta que el gestor/programador que coordina el
 *  servicio la atiende y la marca RESUELTA con una respuesta (ver NovedadSubprocesoService). */
public enum EstadoNovedad {
    ABIERTA,
    RESUELTA
}
