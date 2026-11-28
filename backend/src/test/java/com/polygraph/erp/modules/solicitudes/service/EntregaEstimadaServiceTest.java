package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.shared.service.CalendarioHabilService;
import com.polygraph.erp.shared.service.DiasHabilesService;
import com.polygraph.erp.shared.service.ParametrosTiempo;
import com.polygraph.erp.shared.service.TiempoServicioService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** NS-06: la fecha estimada usa los días hábiles de Colombia, la hora de corte y la hora de Bogotá, y avisa de la pausa por hoja de vida. */
class EntregaEstimadaServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    /** El instante se da en hora de Bogotá (UTC-5) para que la prueba no dependa de la zona de la máquina. */
    private EntregaEstimadaService servicio(String instanteBogota, LocalDate... festivos) {
        Clock reloj = Clock.fixed(java.time.LocalDateTime.parse(instanteBogota).atZone(BOGOTA).toInstant(), BOGOTA);
        var calendario = new CalendarioHabilService(anio -> Set.of(festivos), () -> ParametrosTiempo.POR_DEFECTO, reloj);
        var dias = new DiasHabilesService(calendario);
        return new EntregaEstimadaService(null, new TiempoServicioService(dias), dias);
    }

    private Proceso proceso(int dias, boolean requiereHojaVida) {
        return Proceso.builder().nombreProceso("P").diasHabilesEntrega(dias).requiereHojaVida(requiereHojaVida).build();
    }

    @Test
    void miercoles_antes_del_corte_cuenta_cinco_dias_habiles_y_el_texto_lleva_dia_y_mes() {
        var r = servicio("2026-10-28T10:00:00").estimarDe(List.of(proceso(5, false)));
        assertThat(r.fecha()).isEqualTo(LocalDate.of(2026, 11, 4));   // jue 29, vie 30, lun 2, mar 3, mié 4
        assertThat(r.texto()).isEqualTo("miércoles, 4 de noviembre");
        assertThat(r.despuesDelCorte()).isFalse();
        assertThat(r.pausaPorHojaDeVida()).isFalse();
        assertThat(r.nota()).isEmpty();
    }

    @Test
    void un_festivo_no_cuenta() {
        var r = servicio("2026-10-28T10:00:00", LocalDate.of(2026, 11, 2)).estimarDe(List.of(proceso(5, false)));
        assertThat(r.fecha()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(r.texto()).isEqualTo("jueves, 5 de noviembre");
    }

    @Test
    void despues_de_las_4pm_cuenta_desde_el_siguiente_dia_habil() {
        var r = servicio("2026-10-28T17:30:00").estimarDe(List.of(proceso(5, false)));
        assertThat(r.recepcion()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(r.fecha()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(r.despuesDelCorte()).isTrue();
        assertThat(r.nota()).contains("jueves, 29 de octubre");
    }

    @Test
    void la_hora_se_mira_en_bogota_no_en_la_del_servidor() {
        // 4:30 p. m. en Bogotá = 21:30 UTC: ya pasó el corte aunque en UTC "sea de noche".
        var r = servicio("2026-10-28T16:30:00").estimarDe(List.of(proceso(1, false)));
        assertThat(r.despuesDelCorte()).isTrue();
        assertThat(r.fecha()).isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    void viernes_en_la_tarde_y_fin_de_semana_pasan_al_lunes() {
        var r = servicio("2026-10-30T17:00:00").estimarDe(List.of(proceso(1, false)));
        assertThat(r.recepcion()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(r.fecha()).isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    void con_varios_servicios_manda_el_plazo_mayor() {
        var r = servicio("2026-10-28T10:00:00").estimarDe(List.of(proceso(2, false), proceso(6, false)));
        assertThat(r.diasHabiles()).isEqualTo(6);
        assertThat(r.fecha()).isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void si_algun_servicio_necesita_la_hoja_de_vida_avisa_de_la_pausa() {
        var r = servicio("2026-10-28T10:00:00").estimarDe(List.of(proceso(5, false), proceso(5, true)));
        assertThat(r.pausaPorHojaDeVida()).isTrue();
        assertThat(r.nota()).contains("hoja de vida");
    }

    @Test
    void sin_plazo_en_el_catalogo_usa_cinco_dias() {
        var r = servicio("2026-10-28T10:00:00").estimarDe(List.of(Proceso.builder().nombreProceso("P").requiereHojaVida(false).build()));
        assertThat(r.diasHabiles()).isEqualTo(5);
    }
}
