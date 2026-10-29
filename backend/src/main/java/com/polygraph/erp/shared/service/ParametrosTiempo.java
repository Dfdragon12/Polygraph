package com.polygraph.erp.shared.service;

import java.time.LocalTime;
import java.time.ZoneId;

/** Parámetros del cálculo de tiempos: zona horaria, hora de corte, umbral de "por vencer" (en días hábiles) y contra qué fecha se mide el
 *  cumplimiento de una reprogramación por causa de Polygraph (la original, o la vigente). Si la causa es del cliente o del candidato
 *  se mide siempre contra la nueva fecha. */
public record ParametrosTiempo(ZoneId zona, LocalTime horaCorte, int umbralPorVencerDias, boolean polygraphMideContraOriginal) {

    public static final ParametrosTiempo POR_DEFECTO = new ParametrosTiempo(ZoneId.of("America/Bogota"), LocalTime.of(16, 0), 1, true);

    public ParametrosTiempo(ZoneId zona, LocalTime horaCorte, int umbralPorVencerDias) {
        this(zona, horaCorte, umbralPorVencerDias, true);
    }
}
