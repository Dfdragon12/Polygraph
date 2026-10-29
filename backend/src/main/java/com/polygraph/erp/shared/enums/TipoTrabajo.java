package com.polygraph.erp.shared.enums;

/** Tipos de trabajo pesado que pasan por la cola. Cada tipo tiene su propio pool de hilos
 *  (app.trabajos.tipos.*), así que uno saturado no frena a los demás. */
public enum TipoTrabajo {
    /** Excel de solicitudes: RAM (el libro se abre entero) y una transacción por fila. */
    CARGA_MASIVA,
    /** Informe Word → PDF con LibreOffice: CPU, RAM fuera del heap y disco temporal. */
    INFORME_PDF,
    /** Mismo documento, pedido por roles internos desde el formulario (cualquier estado). */
    FORMULARIO_PDF,
    /** Recalcula el estado de tiempo (semáforo) de las solicitudes en curso: el estado cambia solo con el paso de los días. */
    RECALCULO_TIEMPO
}
