package com.polygraph.erp.shared.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FestivosColombiaGenerator — Ley Emiliani + Pascua")
class FestivosColombiaGeneratorTest {

    private static boolean esFestivo(LocalDate fecha) {
        return FestivosColombiaGenerator.generar(fecha.getYear()).containsKey(fecha);
    }

    // Calendario 2025 publicado oficialmente: referencia conocida para los fijos, los trasladados y los de Pascua.
    @Test
    @DisplayName("Festivos fijos de 2025")
    void fijos2025() {
        for (LocalDate f : new LocalDate[]{LocalDate.of(2025, 1, 1), LocalDate.of(2025, 5, 1), LocalDate.of(2025, 7, 20),
                LocalDate.of(2025, 8, 7), LocalDate.of(2025, 12, 8), LocalDate.of(2025, 12, 25)}) {
            assertThat(esFestivo(f)).as(f.toString()).isTrue();
        }
    }

    @Test
    @DisplayName("Festivos de 2025 trasladados al lunes (Ley Emiliani) y los de Pascua")
    void trasladadosYPascua2025() {
        assertThat(esFestivo(LocalDate.of(2025, 1, 6))).isTrue();    // Reyes (ya era lunes)
        assertThat(esFestivo(LocalDate.of(2025, 3, 24))).isTrue();   // San José (19-mar miércoles → lunes 24)
        assertThat(esFestivo(LocalDate.of(2025, 3, 19))).isFalse();
        assertThat(esFestivo(LocalDate.of(2025, 4, 17))).isTrue();   // Jueves Santo
        assertThat(esFestivo(LocalDate.of(2025, 4, 18))).isTrue();   // Viernes Santo
        assertThat(esFestivo(LocalDate.of(2025, 6, 2))).isTrue();    // Ascensión
        assertThat(esFestivo(LocalDate.of(2025, 6, 23))).isTrue();   // Corpus Christi
        assertThat(esFestivo(LocalDate.of(2025, 6, 30))).isTrue();   // Sagrado Corazón / San Pedro y San Pablo
        assertThat(esFestivo(LocalDate.of(2025, 10, 13))).isTrue();  // Día de la Raza
    }

    @Test
    @DisplayName("Un día normal no es festivo")
    void diaNormal() {
        assertThat(esFestivo(LocalDate.of(2025, 2, 12))).isFalse();
    }

    // Valores esperados del prompt de tiempos: deben salir exactos.
    @Test
    @DisplayName("Los 18 festivos de 2026 son exactamente los esperados")
    void festivos2026() {
        assertThat(new TreeSet<>(FestivosColombiaGenerator.generar(2026).keySet())).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 12), LocalDate.of(2026, 3, 23), LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 18), LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 29),
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 17), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 25));
    }

    @Test
    @DisplayName("Los 18 festivos de 2027 son exactamente los esperados")
    void festivos2027() {
        assertThat(new TreeSet<>(FestivosColombiaGenerator.generar(2027).keySet())).containsExactly(
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 11), LocalDate.of(2027, 3, 22), LocalDate.of(2027, 3, 25), LocalDate.of(2027, 3, 26),
                LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 10), LocalDate.of(2027, 5, 31), LocalDate.of(2027, 6, 7), LocalDate.of(2027, 7, 5),
                LocalDate.of(2027, 7, 20), LocalDate.of(2027, 8, 7), LocalDate.of(2027, 8, 16), LocalDate.of(2027, 10, 18), LocalDate.of(2027, 11, 1),
                LocalDate.of(2027, 11, 15), LocalDate.of(2027, 12, 8), LocalDate.of(2027, 12, 25));
    }

    @Test
    @DisplayName("2025 a 2030: 17 o 18 fechas por año (17 cuando dos festivos caen el mismo lunes, y entonces el nombre trae los dos)")
    void fechasPorAnio() {
        for (int anio = 2025; anio <= 2030; anio++) {
            var festivos = FestivosColombiaGenerator.generar(anio);
            assertThat(festivos.size()).as("año %d", anio).isBetween(17, 18);
            assertThat(festivos.values()).allSatisfy(n -> assertThat(n).isNotBlank());
        }
        assertThat(FestivosColombiaGenerator.generar(2025).get(LocalDate.of(2025, 6, 30))).isEqualTo("San Pedro y San Pablo / Sagrado Corazón de Jesús");
    }
}
