package com.polygraph.erp.shared.enums;

/** Estado de un trabajo de la cola persistente (tabla trabajos_asincronos). FALLIDO hace de
 *  "dead letter queue": el trabajo agotó sus intentos o tuvo un error que no vale la pena
 *  reintentar, y queda a la vista del admin para revisarlo o reintentarlo. */
public enum EstadoTrabajo {
    EN_COLA,
    PROCESANDO,
    COMPLETADO,
    FALLIDO;

    public boolean terminado() {
        return this == COMPLETADO || this == FALLIDO;
    }
}
