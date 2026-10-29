package com.polygraph.erp.shared.estados;

import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.Rol;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Las alertas que van APARTE del semáforo (SE-09), debajo de él y con otro estilo: no son estados, son cosas que piden atención. Pura: recibe las
 * filas del alcance de quien pregunta y la hora de Colombia.
 */
public final class AlertasAgenda {

    private AlertasAgenda() {}

    public enum Alerta { SIN_ASIGNAR, NOVEDADES_ABIERTAS, CITAS_DE_HOY, VENCIDAS, PARA_HOY, POR_CONFIRMAR }

    /** Un subproceso del alcance con lo que las alertas necesitan saber de él y de su servicio. */
    public record Fila(Integer idServicio, Long idSubproceso, EstadoSubproceso estado, boolean conResponsable, LocalDateTime fechaProgramada, Rol rolTipo,
                       EstadoTiempo tiempo, Integer diasRestantes, boolean iniciado) {}

    /** Cantidades; una alerta que no aplica al perfil viene en null (la pantalla no la dibuja). */
    public record Resultado(Long sinAsignar, Long novedadesAbiertas, Long citasDeHoy, Long vencidas, Long paraHoy, Long porConfirmar) {}

    /** Qué alertas le corresponden a cada perfil. */
    public static Set<Alerta> aplicables(Rol rol) {
        return switch (rol) {
            case ADMIN_CLIENTE, ANALISTA_CLIENTE -> EnumSet.of(Alerta.VENCIDAS, Alerta.PARA_HOY);
            case GESTOR, PROGRAMADOR, ADMIN_POLYGRAPH -> EnumSet.allOf(Alerta.class);
            case POLIGRAFISTA, VISITADOR -> EnumSet.of(Alerta.NOVEDADES_ABIERTAS, Alerta.CITAS_DE_HOY, Alerta.VENCIDAS, Alerta.PARA_HOY, Alerta.POR_CONFIRMAR);
            case ANALISTA_INTERNO -> EnumSet.of(Alerta.NOVEDADES_ABIERTAS, Alerta.VENCIDAS, Alerta.PARA_HOY);
            default -> EnumSet.noneOf(Alerta.class);
        };
    }

    private static boolean esCita(Rol rol) {
        return rol == Rol.POLIGRAFISTA || rol == Rol.VISITADOR;
    }

    /**
     * Sin asignar y las citas son SUBPROCESOS; Vencidas y Para hoy son SERVICIOS (cada uno cuenta una vez, aunque tenga varios subprocesos en el alcance).
     * Para hoy = la entrega al cliente vence hoy (por vencer con 0 días hábiles restantes).
     */
    public static Resultado calcular(Rol rol, Collection<Fila> filas, long novedadesAbiertas, LocalDateTime ahora) {
        Set<Alerta> aplican = aplicables(rol);
        long sinAsignar = 0, citasDeHoy = 0, porConfirmar = 0;
        Set<Integer> vencidas = new HashSet<>(), paraHoy = new HashSet<>();
        for (Fila f : filas) {
            if ((f.estado() == EstadoSubproceso.PENDIENTE || f.estado() == EstadoSubproceso.PROGRAMANDO) && !f.conResponsable()) sinAsignar++;
            if (esCita(f.rolTipo()) && f.estado() == EstadoSubproceso.EN_EJECUCION && f.fechaProgramada() != null
                    && f.fechaProgramada().toLocalDate().equals(ahora.toLocalDate())) citasDeHoy++;
            if (ReglasCita.porConfirmar(esCita(f.rolTipo()), f.estado(), f.iniciado(), f.fechaProgramada(), ahora, ReglasCita.TOLERANCIA_MINUTOS)) porConfirmar++;
            if (f.tiempo() == EstadoTiempo.VENCIDA) vencidas.add(f.idServicio());
            if (f.tiempo() == EstadoTiempo.POR_VENCER && f.diasRestantes() != null && f.diasRestantes() == 0) paraHoy.add(f.idServicio());
        }
        return new Resultado(
                aplican.contains(Alerta.SIN_ASIGNAR) ? sinAsignar : null,
                aplican.contains(Alerta.NOVEDADES_ABIERTAS) ? novedadesAbiertas : null,
                aplican.contains(Alerta.CITAS_DE_HOY) ? citasDeHoy : null,
                aplican.contains(Alerta.VENCIDAS) ? (long) vencidas.size() : null,
                aplican.contains(Alerta.PARA_HOY) ? (long) paraHoy.size() : null,
                aplican.contains(Alerta.POR_CONFIRMAR) ? porConfirmar : null);
    }
}
