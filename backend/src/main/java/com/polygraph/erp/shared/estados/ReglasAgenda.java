package com.polygraph.erp.shared.estados;

import com.polygraph.erp.shared.enums.EstadoSubproceso;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Las reglas de la Agenda (SE-08), puras (sin base de datos ni reloj): la jornada, el armado del día de una persona por bloques
 * (ocupado / libre / bloqueado) y cuándo un subproceso está «atrasado en agenda».
 */
public final class ReglasAgenda {

    /** La jornada de trabajo: 7:00 a. m. a 6:00 p. m. */
    public static final LocalTime JORNADA_INICIO = LocalTime.of(7, 0);
    public static final LocalTime JORNADA_FIN = LocalTime.of(18, 0);
    /** Almuerzo: se bloquea y no se ofrece para agendar. */
    public static final LocalTime ALMUERZO_INICIO = LocalTime.of(12, 0);
    public static final LocalTime ALMUERZO_FIN = LocalTime.of(13, 0);
    /**
     * Minutos de desplazamiento que se bloquean DESPUÉS de cada cita presencial. Hoy 0 (no se bloquea nada): la especificación pide bloquear el
     * desplazamiento pero no dice cuánto; se cambia aquí cuando se defina.
     */
    public static final int DESPLAZAMIENTO_MINUTOS = 0;

    public static final String MOTIVO_ALMUERZO = "ALMUERZO";
    public static final String MOTIVO_DESPLAZAMIENTO = "DESPLAZAMIENTO";

    private ReglasAgenda() {}

    public enum TipoBloque { OCUPADO, LIBRE, BLOQUEADO }

    /** Una cita o tarea programada de la persona ese día. {@code conDesplazamiento}: después de ella hay que trasladarse (visita presencial). */
    public record Programado(Long idSubproceso, LocalDateTime inicio, int duracionMinutos, String subproceso, String evaluado, Integer idServicio,
                             boolean conDesplazamiento) {
        LocalDateTime fin() { return inicio.plusMinutes(duracionMinutos); }
    }

    /** Un tramo del día. Para OCUPADO trae el subproceso; para BLOQUEADO, el motivo (ALMUERZO o DESPLAZAMIENTO). */
    public record Bloque(TipoBloque tipo, LocalDateTime inicio, LocalDateTime fin, String motivo, Long idSubproceso, String subproceso,
                         String evaluado, Integer idServicio) {
        public long minutos() { return Duration.between(inicio, fin).toMinutes(); }
    }

    /**
     * Arma el día completo de la jornada en tramos seguidos, sin huecos ni cruces. Lo ocupado manda sobre lo bloqueado (una cita que cae en el
     * almuerzo sigue siendo una cita). Resolución de un minuto: lo que se sale de la jornada se recorta.
     */
    public static List<Bloque> armarDia(LocalDate fecha, List<Programado> programados, int desplazamientoMinutos) {
        LocalDateTime ini = fecha.atTime(JORNADA_INICIO);
        LocalDateTime fin = fecha.atTime(JORNADA_FIN);
        int total = (int) Duration.between(ini, fin).toMinutes();
        // celda = minuto de la jornada: -1 libre, -2 almuerzo, -3 desplazamiento, >= 0 índice de la cita que lo ocupa
        int[] celdas = new int[total];
        java.util.Arrays.fill(celdas, -1);
        marcar(celdas, ini, fecha.atTime(ALMUERZO_INICIO), fecha.atTime(ALMUERZO_FIN), -2, false);
        List<Programado> ordenados = programados.stream().sorted(java.util.Comparator.comparing(Programado::inicio)).toList();
        if (desplazamientoMinutos > 0) {
            for (Programado p : ordenados) {
                if (p.conDesplazamiento()) marcar(celdas, ini, p.fin(), p.fin().plusMinutes(desplazamientoMinutos), -3, false);
            }
        }
        for (int i = 0; i < ordenados.size(); i++) marcar(celdas, ini, ordenados.get(i).inicio(), ordenados.get(i).fin(), i, true);

        List<Bloque> bloques = new ArrayList<>();
        int desde = 0;
        for (int m = 1; m <= total; m++) {
            if (m == total || celdas[m] != celdas[desde]) {
                bloques.add(bloque(celdas[desde], ini.plusMinutes(desde), ini.plusMinutes(m), ordenados));
                desde = m;
            }
        }
        return bloques;
    }

    private static void marcar(int[] celdas, LocalDateTime inicioJornada, LocalDateTime desde, LocalDateTime hasta, int valor, boolean sobreescribe) {
        int a = (int) Math.max(0, Duration.between(inicioJornada, desde).toMinutes());
        int b = (int) Math.min(celdas.length, Duration.between(inicioJornada, hasta).toMinutes());
        for (int m = a; m < b; m++) {
            if (sobreescribe || celdas[m] == -1) celdas[m] = valor;
        }
    }

    private static Bloque bloque(int celda, LocalDateTime inicio, LocalDateTime fin, List<Programado> ordenados) {
        if (celda == -1) return new Bloque(TipoBloque.LIBRE, inicio, fin, null, null, null, null, null);
        if (celda == -2) return new Bloque(TipoBloque.BLOQUEADO, inicio, fin, MOTIVO_ALMUERZO, null, null, null, null);
        if (celda == -3) return new Bloque(TipoBloque.BLOQUEADO, inicio, fin, MOTIVO_DESPLAZAMIENTO, null, null, null, null);
        Programado p = ordenados.get(celda);
        return new Bloque(TipoBloque.OCUPADO, inicio, fin, null, p.idSubproceso(), p.subproceso(), p.evaluado(), p.idServicio());
    }

    public static long minutos(List<Bloque> bloques, TipoBloque tipo) {
        return bloques.stream().filter(b -> b.tipo() == tipo).mapToLong(Bloque::minutos).sum();
    }

    /** ¿Cabe una tarea de esta duración, completa, en este tramo libre? */
    public static boolean cabe(Bloque libre, int duracionMinutos) {
        return libre.tipo() == TipoBloque.LIBRE && libre.minutos() >= duracionMinutos;
    }

    /**
     * «Atrasado en agenda»: el subproceso sigue en ejecución y el DÍA en que estaba programado ya pasó sin hacerse. Un nombre distinto de «Vencida»
     * (esa es la entrega al cliente). Se cuenta en días hábiles.
     */
    public static boolean atrasadoEnAgenda(EstadoSubproceso estado, LocalDateTime fechaProgramada, LocalDate hoy) {
        return estado == EstadoSubproceso.EN_EJECUCION && fechaProgramada != null && fechaProgramada.toLocalDate().isBefore(hoy);
    }
}
