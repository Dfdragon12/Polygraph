package com.polygraph.erp.shared.estados;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TextosAvisoTest {

    @Test
    void publicadoParcialDiceQueSePuedeDescargarSinEsperar() {
        String[] t = TextosAviso.publicado("Antecedentes", "Ana Pérez", false, LocalDate.of(2026, 10, 27));
        assertThat(t[0]).isEqualTo("Antecedentes publicado");
        assertThat(t[1]).contains("27 de octubre de 2026").contains("sin esperar").doesNotContain("2026-10-27");
    }

    @Test
    void ultimoPublicadoAvisaQueElEstudioEstaCompleto() {
        String[] t = TextosAviso.publicado("Visita", "Ana Pérez", true, LocalDate.of(2026, 10, 27));
        assertThat(t[0]).isEqualTo("Tu estudio está completo");
        assertThat(t[1]).contains("Ana Pérez").contains("27 de octubre de 2026");
    }

    @Test
    void avisoDeNoAsistiraEsExplicitoParaElProfesional() {
        String[] t = TextosAviso.evaluadoNoAsistira("la cita de Poligrafía de Ana Pérez del 28 de octubre de 2026 a las 9:00 a. m.", "Tiene un viaje");
        assertThat(t[0]).isEqualTo("El evaluado no asistirá");
        assertThat(t[1]).contains("avisó que no asistirá").contains("(Tiene un viaje)");
        assertThat(TextosAviso.evaluadoNoAsistira("la cita", null)[1]).doesNotContain("()");
    }
}
