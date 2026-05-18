package com.polygraph.erp.shared.service;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class DiasHabilesService {

    private static final LocalTime HORA_CORTE = LocalTime.of(16, 0);

    public LocalDate calcularFechaEntrega(LocalDateTime fechaHoraSolicitud, int diasHabiles) {
        LocalDate fechaInicio = determinarFechaInicio(fechaHoraSolicitud);
        return agregarDiasHabiles(fechaInicio, diasHabiles);
    }

    private LocalDate determinarFechaInicio(LocalDateTime fechaHoraSolicitud) {
        LocalDate fecha = fechaHoraSolicitud.toLocalDate();
        LocalTime hora = fechaHoraSolicitud.toLocalTime();

        // Solicitud después de 4pm o en fin de semana → siguiente día hábil
        if (hora.isAfter(HORA_CORTE) || !esDiaHabilElaboracion(fecha)) {
            return siguienteDiaHabilElaboracion(fecha.plusDays(1));
        }
        return fecha;
    }

    // Días hábiles de elaboración: lunes a viernes
    private boolean esDiaHabilElaboracion(LocalDate fecha) {
        DayOfWeek dia = fecha.getDayOfWeek();
        return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY;
    }

    private LocalDate siguienteDiaHabilElaboracion(LocalDate desde) {
        LocalDate fecha = desde;
        while (!esDiaHabilElaboracion(fecha)) {
            fecha = fecha.plusDays(1);
        }
        return fecha;
    }

    // Sábado cuenta como día de entrega pero no de elaboración
    private LocalDate agregarDiasHabiles(LocalDate fechaInicio, int dias) {
        LocalDate fecha = fechaInicio;
        int diasContados = 0;
        while (diasContados < dias) {
            fecha = fecha.plusDays(1);
            if (esDiaHabilElaboracion(fecha)) {
                diasContados++;
            }
        }
        // Si el siguiente día es sábado puede ser el día de entrega real,
        // pero retornamos el viernes calculado (la empresa entrega el sábado si quiere)
        return fecha;
    }
}
