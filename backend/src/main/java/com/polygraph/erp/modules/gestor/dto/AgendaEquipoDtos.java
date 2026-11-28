package com.polygraph.erp.modules.gestor.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Los datos de la Agenda del equipo (SE-08). Las horas salen en hora de Colombia. */
public final class AgendaEquipoDtos {

    private AgendaEquipoDtos() {}

    /**
     * Una persona en la vista «Equipo hoy»: sus minutos ocupados y libres dentro de la jornada (7:00 a. m. – 6:00 p. m.), lo bloqueado
     * (almuerzo/desplazamiento), cuántas citas o tareas tiene ese día y cuántas tiene atrasadas en agenda.
     */
    public record MiembroEquipo(Long idUsuario, String nombre, String apellido, String rol, long minutosOcupados, long minutosLibres,
                                long minutosBloqueados, int citas, int atrasados) {}

    public record EquipoHoy(LocalDate fecha, boolean esDiaHabil, String motivoNoHabil, List<MiembroEquipo> equipo) {}

    /** Un tramo del día: OCUPADO (con su subproceso), LIBRE o BLOQUEADO (motivo ALMUERZO o DESPLAZAMIENTO). */
    public record BloqueDia(String tipo, LocalDateTime inicio, LocalDateTime fin, long minutos, String motivo, Long idSubproceso,
                            String subproceso, String evaluado, Integer idServicio) {}

    public record DiaPersona(Long idUsuario, String nombre, String apellido, String rol, LocalDate fecha, boolean esDiaHabil, String motivoNoHabil,
                             LocalDateTime jornadaInicio, LocalDateTime jornadaFin, long minutosOcupados, long minutosLibres, long minutosBloqueados,
                             List<BloqueDia> bloques) {}

    /** Un día que no se trabaja: sábado, domingo o festivo. */
    public record DiaNoHabil(LocalDate fecha, String motivo) {}
}
