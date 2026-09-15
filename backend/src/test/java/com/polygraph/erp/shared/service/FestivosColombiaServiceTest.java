package com.polygraph.erp.shared.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FestivosColombiaService — Cálculo de festivos (Ley Emiliani + Pascua)")
class FestivosColombiaServiceTest {

    private final FestivosColombiaService service = new FestivosColombiaService();

    // Calendario de festivos 2025 publicado oficialmente — usado como referencia conocida para
    // verificar tanto los fijos como los trasladados por Ley Emiliani y los basados en Pascua.
    @Test
    @DisplayName("Festivos fijos de 2025")
    void festivosFijos2025() {
        assertThat(service.esFestivo(LocalDate.of(2025, 1, 1))).isTrue();  // Año Nuevo
        assertThat(service.esFestivo(LocalDate.of(2025, 5, 1))).isTrue();  // Día del Trabajo
        assertThat(service.esFestivo(LocalDate.of(2025, 7, 20))).isTrue(); // Independencia
        assertThat(service.esFestivo(LocalDate.of(2025, 8, 7))).isTrue();  // Batalla de Boyacá
        assertThat(service.esFestivo(LocalDate.of(2025, 12, 8))).isTrue(); // Inmaculada Concepción
        assertThat(service.esFestivo(LocalDate.of(2025, 12, 25))).isTrue(); // Navidad
    }

    @Test
    @DisplayName("Festivos trasladados al lunes (Ley Emiliani) en 2025")
    void festivosTrasladados2025() {
        assertThat(service.esFestivo(LocalDate.of(2025, 1, 6))).isTrue();   // Reyes — ya caía lunes
        assertThat(service.esFestivo(LocalDate.of(2025, 3, 24))).isTrue();  // San José (19 mar, miércoles → lunes 24)
        assertThat(service.esFestivo(LocalDate.of(2025, 8, 18))).isTrue();  // Asunción (15 ago, viernes → lunes 18)
        assertThat(service.esFestivo(LocalDate.of(2025, 10, 13))).isTrue(); // Día de la Raza (12 oct, domingo → lunes 13)
        assertThat(service.esFestivo(LocalDate.of(2025, 11, 3))).isTrue();  // Todos los Santos (1 nov, sábado → lunes 3)
        assertThat(service.esFestivo(LocalDate.of(2025, 11, 17))).isTrue(); // Independencia de Cartagena (11 nov, martes → lunes 17)

        // Las fechas originales (antes de trasladar) NO son festivo cuando no cayeron en lunes
        assertThat(service.esFestivo(LocalDate.of(2025, 3, 19))).isFalse();
        assertThat(service.esFestivo(LocalDate.of(2025, 11, 1))).isFalse();
    }

    @Test
    @DisplayName("Festivos basados en Pascua en 2025 (domingo de Pascua = 20 de abril)")
    void festivosBasadosEnPascua2025() {
        assertThat(service.esFestivo(LocalDate.of(2025, 4, 17))).isTrue(); // Jueves Santo
        assertThat(service.esFestivo(LocalDate.of(2025, 4, 18))).isTrue(); // Viernes Santo
        assertThat(service.esFestivo(LocalDate.of(2025, 6, 2))).isTrue();  // Ascensión (29 may, jueves → lunes 2 jun)
        assertThat(service.esFestivo(LocalDate.of(2025, 6, 23))).isTrue(); // Corpus Christi
        // Sagrado Corazón y San Pedro/San Pablo caen el mismo lunes en 2025 — coincidencia real,
        // no un bug: ambos se trasladan a lunes 30 de junio.
        assertThat(service.esFestivo(LocalDate.of(2025, 6, 30))).isTrue();
    }

    @Test
    @DisplayName("Un martes cualquiera sin festivo no se marca como festivo")
    void diaNormalNoEsFestivo() {
        assertThat(service.esFestivo(LocalDate.of(2025, 9, 16))).isFalse();
    }
}
