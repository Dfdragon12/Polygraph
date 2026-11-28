package com.polygraph.erp.shared.enums;

import java.util.Optional;

/**
 * Cómo se pasó cada estado viejo de un subproceso a los siete de EstadoSubproceso. Es el espejo en Java de la migración V224
 * (que lo hace en SQL); existe para dejar la regla escrita y probada. Nada del sistema en marcha lo usa.
 */
public final class MapeoEstadoSubproceso {

    private MapeoEstadoSubproceso() {}

    /**
     * @param anterior            PENDIENTE, ASIGNADO, COMPLETADO, PUBLICADO o CANCELADO (los cinco de antes)
     * @param servicioCancelado   el servicio completo estaba cancelado
     * @param esCita              poligrafía o visita (las agenda el programador)
     * @param conProgramador      ya estaba encargado a un programador
     * @param conProfesional      ya tenía profesional asignado
     * @return vacío si el estado viejo no es uno de los cinco (no se mapea)
     */
    public static Optional<EstadoSubproceso> desdeAntiguo(String anterior, boolean servicioCancelado, boolean esCita,
                                                          boolean conProgramador, boolean conProfesional) {
        if (anterior == null) return Optional.empty();
        return Optional.ofNullable(switch (anterior) {
            case "CANCELADO" -> EstadoSubproceso.CANCELADO;
            case "PUBLICADO" -> EstadoSubproceso.PUBLICADO;
            case "COMPLETADO" -> EstadoSubproceso.FINALIZADO;
            case "ASIGNADO" -> EstadoSubproceso.EN_EJECUCION;
            case "PENDIENTE" -> {
                if (servicioCancelado) yield EstadoSubproceso.CANCELADO;
                yield esCita && conProgramador && !conProfesional ? EstadoSubproceso.PROGRAMANDO : EstadoSubproceso.PENDIENTE;
            }
            default -> null;
        });
    }
}
