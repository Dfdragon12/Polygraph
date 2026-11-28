package com.polygraph.erp.shared.estados;

import java.time.LocalDate;

/**
 * Periodos del selector de estadísticas (PB-05): Hoy · Última semana · Este mes · Último año · Total. Se cuentan con la fecha de hoy en Colombia.
 * Pura. {@code desde(hoy) == null} significa «sin límite» (Total).
 */
public enum PeriodoEstadistica {
    HOY, SEMANA, MES, ANIO, TOTAL;

    public LocalDate desde(LocalDate hoy) {
        return switch (this) {
            case HOY -> hoy;
            case SEMANA -> hoy.minusDays(6);                     // los últimos 7 días, hoy incluido
            case MES -> hoy.withDayOfMonth(1);
            case ANIO -> hoy.minusYears(1).plusDays(1);          // 12 meses, hoy incluido
            case TOTAL -> null;
        };
    }

    public static PeriodoEstadistica de(String texto) {
        if (texto == null || texto.isBlank()) return ANIO;
        try {
            return valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.polygraph.erp.shared.exceptions.ApiException("Periodo desconocido: " + texto, org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }
}
