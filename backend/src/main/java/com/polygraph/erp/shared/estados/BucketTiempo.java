package com.polygraph.erp.shared.estados;

import com.polygraph.erp.shared.enums.EstadoTiempo;

/**
 * Los cinco grupos de tiempo de las pantallas de inicio y de Solicitudes (PB-05): Vencidas · Vence hoy · Vence mañana · A tiempo · En pausa. No es un
 * estado nuevo: agrupa el {@link EstadoTiempo} que ya guarda el servidor, con los días hábiles que le quedan. Una solicitud ya entregada (o cancelada) no
 * está en ninguno. Pura: se prueba sin base de datos, y {@code ServicioSpecifications.conBucket} dice lo mismo en la consulta de las listas.
 */
public enum BucketTiempo {
    VENCIDAS, VENCE_HOY, VENCE_MANANA, A_TIEMPO, EN_PAUSA;

    /** El grupo de una solicitud abierta; null si ya fue entregada o no tiene estado de tiempo (cancelada). */
    public static BucketTiempo de(EstadoTiempo tiempo, Integer diasRestantesHabiles) {
        if (tiempo == null) return null;
        return switch (tiempo) {
            case VENCIDA -> VENCIDAS;
            case EN_PAUSA -> EN_PAUSA;
            case A_TIEMPO -> A_TIEMPO;
            case POR_VENCER -> {
                int dias = diasRestantesHabiles == null ? 0 : diasRestantesHabiles;
                yield dias <= 0 ? VENCE_HOY : dias == 1 ? VENCE_MANANA : A_TIEMPO;
            }
            case ENTREGADA_A_TIEMPO, ENTREGADA_TARDE -> null;
        };
    }
}
