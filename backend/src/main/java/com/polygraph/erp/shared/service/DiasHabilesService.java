package com.polygraph.erp.shared.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Fechas de entrega y retraso en días hábiles, sobre CalendarioHabilService (que es quien sabe qué día es hábil y qué hora es). */
@Service
@RequiredArgsConstructor
public class DiasHabilesService {

    private final CalendarioHabilService calendario;

    /** Ahora, como fecha y hora de la zona del negocio (America/Bogota). */
    public LocalDateTime ahora() {
        return calendario.ahora().toLocalDateTime();
    }

    public LocalDate hoy() {
        return calendario.hoy();
    }

    /** Fecha de compromiso de una solicitud recibida en fechaHoraSolicitud (hora del negocio): se cuenta desde el día de recepción efectivo
     *  (después de la hora de corte o en día no hábil, el siguiente día hábil) y ese día no cuenta. */
    public LocalDate calcularFechaEntrega(LocalDateTime fechaHoraSolicitud, int diasHabiles) {
        return calendario.sumarDiasHabiles(calendario.fechaRecepcionEfectiva(fechaHoraSolicitud), diasHabiles);
    }

    /** El día en que se considera recibida una solicitud creada en fechaHora (hora del negocio). */
    public LocalDate recepcionEfectiva(LocalDateTime fechaHora) {
        return calendario.fechaRecepcionEfectiva(fechaHora);
    }

    public LocalDate sumarDiasHabiles(LocalDate inicio, int dias) {
        return calendario.sumarDiasHabiles(inicio, dias);
    }

    /** Último día hábil que sea hoy o anterior: una fecha de entrega ANTERIOR a este día está vencida (al menos un día hábil de retraso). */
    public LocalDate ultimoDiaHabilHasta(LocalDate hoy) {
        return calendario.ultimoDiaHabilHasta(hoy);
    }

    /** Días hábiles de retraso de una entrega hasta hoy; 0 si no hay fecha o aún está a tiempo. */
    public int diasRetraso(LocalDate entrega, LocalDate hoy) {
        return entrega == null ? 0 : Math.max(0, calendario.diasHabilesEntre(entrega, hoy));
    }
}
