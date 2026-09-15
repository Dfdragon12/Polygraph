package com.polygraph.erp.shared.service;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** Festivos colombianos (Ley 51 de 1983 — "Ley Emiliani"): los que no tienen fecha fija (Reyes,
 *  San José, Ascensión, Corpus Christi, Sagrado Corazón, San Pedro y San Pablo, Asunción, Día de
 *  la Raza, Todos los Santos, Independencia de Cartagena) se trasladan al lunes siguiente si no
 *  cayeron ya en lunes. Los basados en Pascua (Jueves/Viernes Santo, y los tres anteriores que
 *  también dependen de la fecha de Pascua) se calculan con el algoritmo de Meeus/Jones/Butcher
 *  para Pascua en el calendario gregoriano. */
@Service
public class FestivosColombiaService {

    private final Map<Integer, Set<LocalDate>> cachePorAnio = new ConcurrentHashMap<>();

    public boolean esFestivo(LocalDate fecha) {
        return festivosDelAnio(fecha.getYear()).contains(fecha);
    }

    public Set<LocalDate> festivosDelAnio(int anio) {
        return cachePorAnio.computeIfAbsent(anio, this::calcularFestivos);
    }

    private Set<LocalDate> calcularFestivos(int anio) {
        Set<LocalDate> festivos = new HashSet<>();

        // Fijos — no se trasladan
        festivos.add(LocalDate.of(anio, 1, 1));   // Año Nuevo
        festivos.add(LocalDate.of(anio, 5, 1));   // Día del Trabajo
        festivos.add(LocalDate.of(anio, 7, 20));  // Independencia
        festivos.add(LocalDate.of(anio, 8, 7));   // Batalla de Boyacá
        festivos.add(LocalDate.of(anio, 12, 8));  // Inmaculada Concepción
        festivos.add(LocalDate.of(anio, 12, 25)); // Navidad

        // Ley Emiliani — se trasladan al lunes siguiente si no cayeron en lunes
        festivos.add(trasladarALunes(LocalDate.of(anio, 1, 6)));   // Reyes Magos
        festivos.add(trasladarALunes(LocalDate.of(anio, 3, 19)));  // San José
        festivos.add(trasladarALunes(LocalDate.of(anio, 6, 29)));  // San Pedro y San Pablo
        festivos.add(trasladarALunes(LocalDate.of(anio, 8, 15)));  // Asunción de la Virgen
        festivos.add(trasladarALunes(LocalDate.of(anio, 10, 12))); // Día de la Raza
        festivos.add(trasladarALunes(LocalDate.of(anio, 11, 1)));  // Todos los Santos
        festivos.add(trasladarALunes(LocalDate.of(anio, 11, 11))); // Independencia de Cartagena

        // Basados en la fecha de Pascua — Jueves y Viernes Santo no se trasladan (ya caen entre
        // semana); los otros tres sí aplican Ley Emiliani.
        LocalDate domingoPascua = domingoDePascua(anio);
        festivos.add(domingoPascua.minusDays(3));                   // Jueves Santo
        festivos.add(domingoPascua.minusDays(2));                   // Viernes Santo
        festivos.add(trasladarALunes(domingoPascua.plusDays(39)));  // Ascensión del Señor
        festivos.add(trasladarALunes(domingoPascua.plusDays(60)));  // Corpus Christi
        festivos.add(trasladarALunes(domingoPascua.plusDays(68)));  // Sagrado Corazón de Jesús

        return festivos;
    }

    private LocalDate trasladarALunes(LocalDate fecha) {
        if (fecha.getDayOfWeek() == DayOfWeek.MONDAY) return fecha;
        return fecha.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    /** Domingo de Pascua (calendario gregoriano) — algoritmo de Meeus/Jones/Butcher. */
    private LocalDate domingoDePascua(int anio) {
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
