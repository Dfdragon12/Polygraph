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

    private static final String[] MESES_CORTOS = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};

    /** «24 de nov»: la forma corta para frases del seguimiento. Los meses van fijos (sin depender del idioma del servidor). */
    public static String corta(LocalDate fecha) {
        return fecha == null ? "sin fecha" : fecha.getDayOfMonth() + " de " + MESES_CORTOS[fecha.getMonthValue() - 1];
    }
}
