package com.polygraph.erp.shared.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CalendarioHabilService — días hábiles de Colombia")
class CalendarioHabilServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private static CalendarioHabilService calendario(Set<LocalDate> desactivados, Set<LocalDate> propios) {
        return new CalendarioHabilService(anio -> {
            Set<LocalDate> activos = new HashSet<>(FestivosColombiaGenerator.generar(anio).keySet());
            activos.removeAll(desactivados);
            propios.stream().filter(f -> f.getYear() == anio).forEach(activos::add);
            return activos;
        }, () -> new ParametrosTiempo(BOGOTA, LocalTime.of(16, 0), 1), Clock.fixed(Instant.parse("2026-10-19T15:00:00Z"), ZoneOffset.UTC));
    }

    private final CalendarioHabilService cal = calendario(Set.of(), Set.of());

    private static ZonedDateTime bogota(int a, int m, int d, int h, int min) {
        return ZonedDateTime.of(a, m, d, h, min, 0, 0, BOGOTA);
    }

    @Test
    @DisplayName("Día hábil: lunes a viernes que no sea festivo")
    void esDiaHabil() {
        assertThat(cal.esDiaHabil(LocalDate.of(2026, 10, 9))).isTrue();     // viernes
        assertThat(cal.esDiaHabil(LocalDate.of(2026, 10, 10))).isFalse();   // sábado
        assertThat(cal.esDiaHabil(LocalDate.of(2026, 10, 11))).isFalse();   // domingo
        assertThat(cal.esDiaHabil(LocalDate.of(2026, 10, 12))).isFalse();   // festivo (Día de la Raza)
        assertThat(cal.esDiaHabil(LocalDate.of(2026, 10, 13))).isTrue();
    }

    @Test
    @DisplayName("siguienteDiaHabil salta fines de semana y festivos")
    void siguienteDiaHabil() {
        assertThat(cal.siguienteDiaHabil(LocalDate.of(2026, 10, 9))).isEqualTo(LocalDate.of(2026, 10, 13));   // viernes → martes (lunes festivo)
        assertThat(cal.siguienteDiaHabil(LocalDate.of(2026, 10, 14))).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    @DisplayName("Sumar 5 días hábiles desde el 16-sep-2026 da el 23-sep-2026; sumar 2 desde el 15-oct-2026 da el 19-oct-2026")
    void sumarDiasHabiles() {
        assertThat(cal.sumarDiasHabiles(LocalDate.of(2026, 9, 16), 5)).isEqualTo(LocalDate.of(2026, 9, 23));
        assertThat(cal.sumarDiasHabiles(LocalDate.of(2026, 10, 15), 2)).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(cal.sumarDiasHabiles(LocalDate.of(2026, 10, 15), 0)).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    @DisplayName("diasHabilesEntre(23-sep, 19-oct-2026) = 17 y diasHabilesEntre(16-oct, 19-oct-2026) = 1")
    void diasHabilesEntre() {
        assertThat(cal.diasHabilesEntre(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 10, 19))).isEqualTo(17);
        assertThat(cal.diasHabilesEntre(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 19))).isEqualTo(1);
        assertThat(cal.diasHabilesEntre(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 19))).isZero();
    }

    @Test
    @DisplayName("diasHabilesEntre es negativo si hasta es anterior a desde")
    void diasHabilesEntreNegativo() {
        assertThat(cal.diasHabilesEntre(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 16))).isEqualTo(-1);
        assertThat(cal.diasHabilesEntre(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 9, 23))).isEqualTo(-17);
    }

    @Test
    @DisplayName("Creada el viernes a las 16:01 → recibida el lunes hábil siguiente; a las 16:00 en punto, el mismo viernes")
    void horaDeCorte() {
        assertThat(cal.fechaRecepcionEfectiva(bogota(2026, 9, 18, 16, 1))).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(cal.fechaRecepcionEfectiva(bogota(2026, 9, 18, 16, 0))).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(cal.fechaRecepcionEfectiva(bogota(2026, 9, 16, 9, 0))).isEqualTo(LocalDate.of(2026, 9, 16));
    }

    @Test
    @DisplayName("Creada el viernes 9-oct-2026 a las 17:00 → recibida el martes 13-oct-2026 (el lunes 12 es festivo)")
    void despuesDeCorteConFestivo() {
        assertThat(cal.fechaRecepcionEfectiva(bogota(2026, 10, 9, 17, 0))).isEqualTo(LocalDate.of(2026, 10, 13));
    }

    @Test
    @DisplayName("Creada un sábado, domingo o festivo → recibida el siguiente día hábil")
    void diaNoHabil() {
        assertThat(cal.fechaRecepcionEfectiva(bogota(2026, 9, 19, 10, 0))).isEqualTo(LocalDate.of(2026, 9, 21));      // sábado
        assertThat(cal.fechaRecepcionEfectiva(bogota(2026, 9, 20, 10, 0))).isEqualTo(LocalDate.of(2026, 9, 21));      // domingo
        assertThat(cal.fechaRecepcionEfectiva(bogota(2026, 10, 10, 10, 0))).isEqualTo(LocalDate.of(2026, 10, 13));    // sábado de puente
        assertThat(cal.fechaRecepcionEfectiva(bogota(2026, 10, 12, 10, 0))).isEqualTo(LocalDate.of(2026, 10, 13));    // festivo
    }

    @Test
    @DisplayName("La hora se mira en Bogotá aunque la solicitud llegue en UTC: 21:30 UTC del viernes son las 16:30 en Bogotá")
    void zonaHoraria() {
        ZonedDateTime enUtc = ZonedDateTime.of(2026, 9, 18, 21, 30, 0, 0, ZoneOffset.UTC);
        assertThat(cal.fechaRecepcionEfectiva(enUtc)).isEqualTo(LocalDate.of(2026, 9, 21));
        ZonedDateTime antes = ZonedDateTime.of(2026, 9, 18, 20, 30, 0, 0, ZoneOffset.UTC);   // 15:30 en Bogotá
        assertThat(cal.fechaRecepcionEfectiva(antes)).isEqualTo(LocalDate.of(2026, 9, 18));
    }

    @Test
    @DisplayName("Hoy y ahora salen del reloj inyectado, en la zona de Bogotá")
    void relojInyectado() {
        assertThat(cal.hoy()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(cal.ahora().getZone()).isEqualTo(BOGOTA);
        assertThat(cal.ahora().toLocalTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    @DisplayName("Un festivo desactivado manualmente deja de contar, y un día propio agregado sí cuenta")
    void ajustesManuales() {
        LocalDate raza = LocalDate.of(2026, 10, 12);
        CalendarioHabilService sinRaza = calendario(Set.of(raza), Set.of());
        assertThat(sinRaza.esDiaHabil(raza)).isTrue();
        assertThat(sinRaza.diasHabilesEntre(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 13))).isEqualTo(2);
        assertThat(sinRaza.fechaRecepcionEfectiva(bogota(2026, 10, 9, 17, 0))).isEqualTo(raza);

        CalendarioHabilService conCierre = calendario(Set.of(), Set.of(LocalDate.of(2026, 12, 31)));
        assertThat(conCierre.esDiaHabil(LocalDate.of(2026, 12, 31))).isFalse();
        assertThat(conCierre.siguienteDiaHabil(LocalDate.of(2026, 12, 30))).isEqualTo(LocalDate.of(2027, 1, 4));   // 1-ene festivo, 2-3 fin de semana
    }
}
