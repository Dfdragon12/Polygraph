package com.polygraph.erp.shared.enums;

/** Dónde va una solicitud frente a su plazo de entrega, en días hábiles de Colombia. Lo calcula UN solo servicio
 *  (TiempoServicioService) y todas las pantallas lo leen de ahí. */
public enum EstadoTiempo {
    /** Abierta y con margen. */
    A_TIEMPO,
    /** Abierta y la entrega vence dentro de los próximos días hábiles (app.tiempo.dias-por-vencer). */
    POR_VENCER,
    /** Abierta y ya pasó al menos un día hábil de su fecha de entrega. */
    VENCIDA,
    /** El reloj no corre: espera la hoja de vida del candidato (la fecha de entrega se fija cuando la envía). */
    EN_PAUSA,
    ENTREGADA_A_TIEMPO,
    ENTREGADA_TARDE
}
