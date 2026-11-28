package com.polygraph.erp.shared.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.TreeMap;

/** Festivos nacionales de Colombia de un año (Ley 51 de 1983 — "Ley Emiliani"): los que no tienen fecha fija (Reyes, San José, Ascensión,
 *  Corpus Christi, Sagrado Corazón, San Pedro y San Pablo, Asunción, Día de la Raza, Todos los Santos, Independencia de Cartagena) se
 *  trasladan al lunes siguiente si no cayeron ya en lunes. Los basados en Pascua se calculan con el algoritmo de Meeus/Jones/Butcher
 *  (calendario gregoriano). Función pura: sin Spring ni base de datos. */
public final class FestivosColombiaGenerator {

    private FestivosColombiaGenerator() {}

    /** Los festivos del año (fecha → nombre), ordenados por fecha: 18, o 17 fechas distintas cuando dos coinciden en el mismo lunes. */
    public static Map<LocalDate, String> generar(int anio) {
        Map<LocalDate, String> f = new TreeMap<>() {
            // Dos festivos pueden caer el mismo lunes (ej. San Pedro y San Pablo con el Sagrado Corazón): es UNA fecha con los dos nombres.
            @Override
            public String put(LocalDate fecha, String nombre) {
                String previo = get(fecha);
                return super.put(fecha, previo == null ? nombre : previo + " / " + nombre);
            }
        };

        // Fijos — no se trasladan
        f.put(LocalDate.of(anio, 1, 1), "Año Nuevo");
        f.put(LocalDate.of(anio, 5, 1), "Día del Trabajo");
        f.put(LocalDate.of(anio, 7, 20), "Independencia de Colombia");
        f.put(LocalDate.of(anio, 8, 7), "Batalla de Boyacá");
        f.put(LocalDate.of(anio, 12, 8), "Inmaculada Concepción");
        f.put(LocalDate.of(anio, 12, 25), "Navidad");

        // Ley Emiliani — se trasladan al lunes siguiente si no cayeron en lunes
        f.put(alLunes(LocalDate.of(anio, 1, 6)), "Reyes Magos");
        f.put(alLunes(LocalDate.of(anio, 3, 19)), "San José");
        f.put(alLunes(LocalDate.of(anio, 6, 29)), "San Pedro y San Pablo");
        f.put(alLunes(LocalDate.of(anio, 8, 15)), "Asunción de la Virgen");
        f.put(alLunes(LocalDate.of(anio, 10, 12)), "Día de la Raza");
        f.put(alLunes(LocalDate.of(anio, 11, 1)), "Todos los Santos");
        f.put(alLunes(LocalDate.of(anio, 11, 11)), "Independencia de Cartagena");

        // Basados en la Pascua (P = Domingo de Resurrección): Jueves y Viernes Santo no se trasladan; Ascensión (jueves P+39),
        // Corpus Christi (jueves P+60) y Sagrado Corazón (viernes P+68) pasan al lunes siguiente: P+43, P+64 y P+71.
        LocalDate pascua = domingoDePascua(anio);
        f.put(pascua.minusDays(3), "Jueves Santo");
        f.put(pascua.minusDays(2), "Viernes Santo");
        f.put(alLunes(pascua.plusDays(39)), "Ascensión del Señor");
        f.put(alLunes(pascua.plusDays(60)), "Corpus Christi");
        f.put(alLunes(pascua.plusDays(68)), "Sagrado Corazón de Jesús");
        return f;
    }

    private static LocalDate alLunes(LocalDate fecha) {
        return fecha.getDayOfWeek() == DayOfWeek.MONDAY ? fecha : fecha.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    /** Domingo de Pascua (calendario gregoriano) — algoritmo de Meeus/Jones/Butcher. */
    static LocalDate domingoDePascua(int anio) {
        int a = anio % 19;
        int b = anio / 100;
        int c = anio % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(anio, mes, dia);
    }
}
