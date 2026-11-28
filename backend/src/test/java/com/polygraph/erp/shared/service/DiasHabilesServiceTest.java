package com.polygraph.erp.shared.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DiasHabilesService — Pruebas de cálculo de fechas de entrega")
class DiasHabilesServiceTest {

    private DiasHabilesService service;

    // Lunes fijo para todos los tests
    private LocalDate proximoLunes;
    private LocalDate proximoJueves;
    private LocalDate proximoViernes;
    private LocalDate proximoSabado;

    @BeforeEach
    void setUp() {
        // Sin festivos reales acá a propósito — estos tests fijan fechas relativas a "hoy" para no
        // ser frágiles (ver comentario original más abajo), y un festivo colombiano real cayendo
        // sobre una de esas fechas los volvería flaky. El cálculo de festivos en sí se prueba
        // aparte en FestivosColombiaServiceTest.
        service = new DiasHabilesService(new CalendarioHabilService(anio -> java.util.Set.of(), () -> ParametrosTiempo.POR_DEFECTO, java.time.Clock.systemUTC()));
        // Usar un lunes conocido relativo a la fecha actual para evitar tests frágiles
        proximoLunes = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        proximoJueves = proximoLunes.plusDays(3);
        proximoViernes = proximoLunes.plusDays(4);
        proximoSabado = proximoLunes.plusDays(5);
    }

    // ── Regla: antes de 4pm → cuenta el mismo día ───────────────

    @Test
    @DisplayName("Lunes 9am + 3 días hábiles = jueves")
    void lunes_antes_4pm_mas_3_dias_retorna_jueves() {
        LocalDateTime solicitud = proximoLunes.atTime(9, 0);

        LocalDate resultado = service.calcularFechaEntrega(solicitud, 3);

        LocalDate jueves = proximoLunes.plusDays(3);
        assertThat(resultado).isEqualTo(jueves);
    }

    @Test
    @DisplayName("Lunes 9am + 1 día hábil = martes")
    void lunes_antes_4pm_mas_1_dia_retorna_martes() {
        LocalDateTime solicitud = proximoLunes.atTime(8, 30);

        LocalDate resultado = service.calcularFechaEntrega(solicitud, 1);

        assertThat(resultado).isEqualTo(proximoLunes.plusDays(1));
    }

    @Test
    @DisplayName("Lunes 9am + 5 días hábiles = lunes siguiente (salta fin de semana)")
    void lunes_antes_4pm_mas_5_dias_salta_fin_de_semana() {
        LocalDateTime solicitud = proximoLunes.atTime(10, 0);

        LocalDate resultado = service.calcularFechaEntrega(solicitud, 5);

        // +5 hábiles: mar, mié, jue, vie (4 hábiles), sáb/dom saltan, lun siguiente = 5
        LocalDate lunesSiguiente = proximoLunes.plusWeeks(1);
        assertThat(resultado).isEqualTo(lunesSiguiente);
    }

    // ── Regla: después de 4pm → empieza a contar desde el siguiente día hábil ──

    @Test
    @DisplayName("Lunes 5pm + 3 días hábiles = viernes (empieza desde martes)")
    void lunes_despues_4pm_mas_3_dias_retorna_viernes() {
        LocalDateTime solicitud = proximoLunes.atTime(17, 0);

        LocalDate resultado = service.calcularFechaEntrega(solicitud, 3);

        // Empieza desde martes: mié(1), jue(2), vie(3)
        LocalDate viernes = proximoLunes.plusDays(4);
        assertThat(resultado).isEqualTo(viernes);
    }

    @Test
    @DisplayName("Lunes exactamente 4pm NO se considera después del corte (borde)")
    void lunes_exactamente_4pm_no_aplica_regla_tarde() {
        // 4:00pm exacto → hora NO isAfter(16:00) porque isAfter es estrictamente mayor
        LocalDateTime solicitud = proximoLunes.atTime(16, 0);

        LocalDate resultado = service.calcularFechaEntrega(solicitud, 1);

        // Cuenta desde lunes mismo → resultado = martes
        assertThat(resultado).isEqualTo(proximoLunes.plusDays(1));
    }

    @Test
    @DisplayName("Lunes 4:01pm activa la regla del corte y empieza desde martes")
    void lunes_despues_4pm_un_minuto_activa_regla() {
        LocalDateTime solicitud = proximoLunes.atTime(16, 1);

        LocalDate resultado = service.calcularFechaEntrega(solicitud, 1);

        // Empieza desde martes → resultado = miércoles
        assertThat(resultado).isEqualTo(proximoLunes.plusDays(2));
    }

    // ── Regla: solicitud en fin de semana → siguiente hábil ─────

    @Test
    @DisplayName("Sábado → fecha inicio ajustada al lunes siguiente")
    void sabado_ajusta_fecha_inicio_al_lunes() {
        LocalDateTime solicitudSabado = proximoSabado.atTime(10, 0);

        LocalDate resultado = service.calcularFechaEntrega(solicitudSabado, 1);

        // Sábado no es hábil de elaboración → empieza lunes siguiente → resultado = martes
        LocalDate lunesSiguiente = proximoSabado.plusDays(2); // sáb + 2 = lun
        assertThat(resultado).isEqualTo(lunesSiguiente.plusDays(1)); // lun + 1 = mar
    }

    @Test
    @DisplayName("Domingo → fecha inicio ajustada al lunes siguiente")
    void domingo_ajusta_fecha_inicio_al_lunes() {
        LocalDate domingo = proximoSabado.plusDays(1);
        LocalDateTime solicitudDomingo = domingo.atTime(11, 0);

        LocalDate resultado = service.calcularFechaEntrega(solicitudDomingo, 1);

        // Domingo → lunes siguiente → +1 hábil = martes
        LocalDate lunesSiguiente = domingo.plusDays(1);
        assertThat(resultado).isEqualTo(lunesSiguiente.plusDays(1));
    }

    // ── Comportamiento del sábado en ventana de entrega ─────────

    @Test
    @DisplayName("Jueves + 2 días hábiles = lunes (sábado y domingo no cuentan como elaboración)")
    void jueves_mas_2_dias_habiles_retorna_lunes() {
        LocalDateTime solicitud = proximoJueves.atTime(9, 0);

        LocalDate resultado = service.calcularFechaEntrega(solicitud, 2);

        // jue → vie(1), sáb(no cuenta), dom(no cuenta), lun(2) → lunes
        LocalDate lunesSiguiente = proximoJueves.plusDays(4); // jue+4 = lun
        assertThat(resultado).isEqualTo(lunesSiguiente);
    }

    @Test
    @DisplayName("Viernes 9am + 1 día hábil = lunes (no sábado)")
    void viernes_mas_1_dia_retorna_lunes_no_sabado() {
        LocalDateTime solicitud = proximoViernes.atTime(9, 0);

        LocalDate resultado = service.calcularFechaEntrega(solicitud, 1);

        // vie → sáb(no cuenta), dom(no cuenta), lun(1) → lunes
        LocalDate lunesSiguiente = proximoViernes.plusDays(3); // vie+3 = lun
        assertThat(resultado).isEqualTo(lunesSiguiente);
    }

    // ── Casos con Estudio Quick (1 día hábil) ───────────────────

    @Test
    @DisplayName("Estudio Quick (1 día) en lunes 9am → entrega martes")
    void estudio_quick_lunes_entrega_martes() {
        LocalDateTime solicitud = proximoLunes.atTime(9, 0);

        LocalDate resultado = service.calcularFechaEntrega(solicitud, 1);

        assertThat(resultado).isEqualTo(proximoLunes.plusDays(1));
    }

    @Test
    @DisplayName("Estudio Quick (1 día) después de 4pm en lunes → entrega miércoles")
    void estudio_quick_lunes_tarde_entrega_miercoles() {
        LocalDateTime solicitud = proximoLunes.atTime(17, 30);

        LocalDate resultado = service.calcularFechaEntrega(solicitud, 1);

        // Empieza martes → +1 = miércoles
        assertThat(resultado).isEqualTo(proximoLunes.plusDays(2));
    }

    // ───────────────────────────── retraso en días hábiles ─────────────────────────────

    @Test
    @DisplayName("Retraso: entrega el viernes y hoy es lunes → 1 día hábil (el fin de semana no cuenta)")
    void retraso_cuenta_solo_dias_habiles() {
        assertThat(service.diasRetraso(proximoViernes, proximoViernes.plusDays(3))).isEqualTo(1);   // lunes
        assertThat(service.diasRetraso(proximoViernes, proximoViernes.plusDays(7))).isEqualTo(5);   // viernes siguiente
    }

    @Test
    @DisplayName("Retraso: el sábado y el domingo después de una entrega del viernes aún no son retraso")
    void retraso_fin_de_semana_no_es_retraso() {
        assertThat(service.diasRetraso(proximoViernes, proximoViernes)).isZero();
        assertThat(service.diasRetraso(proximoViernes, proximoViernes.plusDays(1))).isZero();
        assertThat(service.diasRetraso(proximoViernes, proximoViernes.plusDays(2))).isZero();
        assertThat(service.diasRetraso((LocalDate) null, proximoViernes)).isZero();
    }

    @Test
    @DisplayName("El corte de vencidas es el último día hábil: en fin de semana es el viernes")
    void corte_de_vencidas() {
        assertThat(service.ultimoDiaHabilHasta(proximoLunes)).isEqualTo(proximoLunes);
        assertThat(service.ultimoDiaHabilHasta(proximoSabado)).isEqualTo(proximoViernes);
        assertThat(service.ultimoDiaHabilHasta(proximoSabado.plusDays(1))).isEqualTo(proximoViernes);
        // Vencida (fecha < corte) coincide con "al menos un día hábil de retraso" en cualquier día de la semana.
        for (int i = 0; i < 14; i++) {
            LocalDate hoy = proximoLunes.plusDays(i);
            for (int j = 1; j <= 10; j++) {
                LocalDate entrega = hoy.minusDays(j);
                assertThat(entrega.isBefore(service.ultimoDiaHabilHasta(hoy))).as("entrega %s, hoy %s", entrega, hoy)
                        .isEqualTo(service.diasRetraso(entrega, hoy) >= 1);
            }
        }
    }

    // ───────── ejemplos reales del prompt de tiempos, con los festivos reales de Colombia ─────────

    private DiasHabilesService conFestivosReales() {
        return new DiasHabilesService(new CalendarioHabilService(anio -> FestivosColombiaGenerator.generar(anio).keySet(), () -> ParametrosTiempo.POR_DEFECTO, java.time.Clock.systemUTC()));
    }

    @Test
    @DisplayName("Plazo: recibida el miércoles 16 sep 2026 con plazo 5 vence el miércoles 23 sep; el jueves 15 oct con plazo 2, el lunes 19 oct")
    void ejemplos_de_plazo() {
        assertThat(conFestivosReales().calcularFechaEntrega(LocalDate.of(2026, 9, 16).atTime(10, 0), 5)).isEqualTo(LocalDate.of(2026, 9, 23));
        assertThat(conFestivosReales().calcularFechaEntrega(LocalDate.of(2026, 10, 15).atTime(10, 0), 2)).isEqualTo(LocalDate.of(2026, 10, 19));
        // Después de las 4 p. m. cuenta como del día hábil siguiente.
        assertThat(conFestivosReales().calcularFechaEntrega(LocalDate.of(2026, 9, 16).atTime(16, 30), 5)).isEqualTo(LocalDate.of(2026, 9, 24));
    }

    @Test
    @DisplayName("Retraso: compromiso 23 sep y hoy 19 oct 2026 son 17 días hábiles (26 en calendario); viernes 16 a lunes 19 oct, 1")
    void ejemplos_de_retraso() {
        DiasHabilesService s = conFestivosReales();
        assertThat(s.diasRetraso(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 10, 19))).isEqualTo(17);
        assertThat(s.diasRetraso(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 19))).isEqualTo(1);
        // Solicitud #12: compromiso 1 sep, publicada 25 sep 2026.
    }
}
