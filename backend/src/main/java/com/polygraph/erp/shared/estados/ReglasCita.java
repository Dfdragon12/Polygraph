package com.polygraph.erp.shared.estados;

import com.polygraph.erp.shared.enums.EstadoSubproceso;

import java.time.LocalDateTime;

/**
 * Las reglas de tiempo de una cita de poligrafía, visita o VerifEye (SE-07). Pura: recibe la hora actual de Colombia, no lee el reloj.
 * El evaluado tiene máximo {@link #TOLERANCIA_MINUTOS} minutos después de la hora de la cita para presentarse.
 */
public final class ReglasCita {

    /** Minutos de espera después de la hora de la cita (configurable en {@code app.citas.tolerancia-minutos}). */
    public static final int TOLERANCIA_MINUTOS = 15;

    private ReglasCita() {}

    /** Una cita que sigue en pie: en ejecución, con fecha, y el informe todavía sin iniciar (nadie confirmó que se realizó). */
    private static boolean enPie(boolean esCita, EstadoSubproceso estado, boolean iniciado, LocalDateTime fecha) {
        return esCita && estado == EstadoSubproceso.EN_EJECUCION && !iniciado && fecha != null;
    }

    /** La hora de la cita ya llegó. */
    public static boolean yaLlegoLaHora(LocalDateTime fecha, LocalDateTime ahora) {
        return fecha != null && !ahora.isBefore(fecha);
    }

    /** Pasó la tolerancia: la hora de la cita + los minutos de espera. */
    public static boolean pasoLaTolerancia(LocalDateTime fecha, LocalDateTime ahora, int minutos) {
        return fecha != null && !ahora.isBefore(fecha.plusMinutes(minutos));
    }

    /**
     * «El evaluado no ha llegado»: el profesional puede avisarle al programador durante el día de la cita, ya pasada la hora, mientras
     * no haya iniciado el informe y no haya avisado antes.
     */
    public static boolean puedeAvisarQueNoHaLlegado(boolean esCita, EstadoSubproceso estado, boolean iniciado, boolean yaAviso,
                                                    LocalDateTime fecha, LocalDateTime ahora) {
        return enPie(esCita, estado, iniciado, fecha) && !yaAviso && yaLlegoLaHora(fecha, ahora) && ahora.toLocalDate().equals(fecha.toLocalDate());
    }

    /** «No se presentó»: solo pasados los 15 minutos de tolerancia. */
    public static boolean puedeRegistrarNoSePresento(boolean esCita, EstadoSubproceso estado, boolean iniciado,
                                                     LocalDateTime fecha, LocalDateTime ahora, int minutos) {
        return enPie(esCita, estado, iniciado, fecha) && pasoLaTolerancia(fecha, ahora, minutos);
    }

    /**
     * Bandeja «Por confirmar»: pasaron los 15 minutos después de la hora de la cita y el profesional no inició el informe ni registró nada.
     * Es la misma condición que «No se presentó», pero vista desde la bandeja del programador y del profesional.
     */
    public static boolean porConfirmar(boolean esCita, EstadoSubproceso estado, boolean iniciado, LocalDateTime fecha, LocalDateTime ahora,
                                       int minutos) {
        return puedeRegistrarNoSePresento(esCita, estado, iniciado, fecha, ahora, minutos);
    }
}
