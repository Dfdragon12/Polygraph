package com.polygraph.erp.shared.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Fechas para leer en un mensaje: "23 de septiembre de 2026", nunca "2026-09-23". */
public final class FechasLegibles {

    private static final DateTimeFormatter LARGA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"));

    private FechasLegibles() {}

    public static String larga(LocalDate fecha) {
        return fecha == null ? "sin fecha" : LARGA.format(fecha);
    }
}
