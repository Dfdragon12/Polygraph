package com.polygraph.erp.shared.service;

import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.ImputableA;
import com.polygraph.erp.shared.service.SemaforoTiempoCalculator.EntradaTiempo;
import com.polygraph.erp.shared.service.SemaforoTiempoCalculator.Lapso;
import com.polygraph.erp.shared.service.SemaforoTiempoCalculator.ResultadoTiempo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SemaforoTiempoCalculator — estado de tiempo en días hábiles de Colombia")
class SemaforoTiempoCalculatorTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private static CalendarioHabilService calendario() {
        return new CalendarioHabilService(anio -> FestivosColombiaGenerator.generar(anio).keySet(), () -> ParametrosTiempo.POR_DEFECTO, Clock.systemUTC());
    }

    private final SemaforoTiempoCalculator calculador = new SemaforoTiempoCalculator(calendario(), ParametrosTiempo.POR_DEFECTO);

    private static ZonedDateTime a(int anio, int mes, int dia) {
        return ZonedDateTime.of(anio, mes, dia, 10, 0, 0, 0, BOGOTA);
    }

    private static LocalDate d(int anio, int mes, int dia) {
        return LocalDate.of(anio, mes, dia);
    }

    private static EntradaTiempo abierta(LocalDate compromiso) {
        return new EntradaTiempo(EstadoServicio.EN_EJECUCION, compromiso, compromiso, null, List.of(), null);
    }

    private ResultadoTiempo calcular(EntradaTiempo e, ZonedDateTime ahora) {
        return calculador.calcular(e, ahora);
    }

    // ───────────────────────── A tiempo / por vencer ─────────────────────────

    @Test
    @DisplayName("A_TIEMPO: faltan 2 o más días hábiles")
    void aTiempo() {
        ResultadoTiempo r = calcular(abierta(d(2026, 9, 23)), a(2026, 9, 14));   // lunes 14 → miércoles 23: 7 días hábiles
        assertThat(r.estado()).isEqualTo(EstadoTiempo.A_TIEMPO);
        assertThat(r.diasRestantesHabiles()).isEqualTo(7);
        assertThat(r.diasRetrasoHabiles()).isZero();
        assertThat(r.fechaCompromisoAjustada()).isEqualTo(d(2026, 9, 23));
    }

    @Test
    @DisplayName("POR_VENCER: vence hoy (se vence al final del día de compromiso, no antes)")
    void porVencerHoy() {
        ResultadoTiempo r = calcular(abierta(d(2026, 9, 23)), a(2026, 9, 23));
        assertThat(r.estado()).isEqualTo(EstadoTiempo.POR_VENCER);
        assertThat(r.diasRestantesHabiles()).isZero();
    }

    @Test
    @DisplayName("POR_VENCER: vence el siguiente día hábil; el viernes con compromiso el lunes también")
    void porVencerSiguienteHabil() {
        assertThat(calcular(abierta(d(2026, 9, 24)), a(2026, 9, 23)).estado()).isEqualTo(EstadoTiempo.POR_VENCER);   // miércoles → jueves
        ResultadoTiempo viernesALunes = calcular(abierta(d(2026, 9, 21)), a(2026, 9, 18));                           // viernes → lunes
        assertThat(viernesALunes.estado()).isEqualTo(EstadoTiempo.POR_VENCER);
        assertThat(viernesALunes.diasRestantesHabiles()).isEqualTo(1);
        assertThat(calcular(abierta(d(2026, 9, 22)), a(2026, 9, 18)).estado()).isEqualTo(EstadoTiempo.A_TIEMPO);     // viernes → martes: 2 días
    }

    @Test
    @DisplayName("POR_VENCER en la víspera de un festivo: viernes 9-oct con compromiso el martes 13 (el lunes 12 es festivo)")
    void porVencerVisperaDeFestivo() {
        ResultadoTiempo r = calcular(abierta(d(2026, 10, 13)), a(2026, 10, 9));
        assertThat(r.estado()).isEqualTo(EstadoTiempo.POR_VENCER);
        assertThat(r.diasRestantesHabiles()).isEqualTo(1);
        assertThat(calcular(abierta(d(2026, 10, 13)), a(2026, 10, 8)).estado()).isEqualTo(EstadoTiempo.A_TIEMPO);    // jueves: 2 días hábiles
    }

    // ───────────────────────── vencida ─────────────────────────

    @Test
    @DisplayName("VENCIDA: compromiso 23-sep y hoy 19-oct-2026 son 17 días hábiles (26 en calendario)")
    void vencida() {
        ResultadoTiempo r = calcular(abierta(d(2026, 9, 23)), a(2026, 10, 19));
        assertThat(r.estado()).isEqualTo(EstadoTiempo.VENCIDA);
        assertThat(r.diasRetrasoHabiles()).isEqualTo(17);
        assertThat(r.diasRestantesHabiles()).isZero();
    }

    @Test
    @DisplayName("VENCIDA: viernes 16-oct a lunes 19-oct es 1 día hábil; el fin de semana ya está vencida pero con 0 días de retraso")
    void vencidaUnDiaYFinDeSemana() {
        assertThat(calcular(abierta(d(2026, 10, 16)), a(2026, 10, 19)).diasRetrasoHabiles()).isEqualTo(1);
        ResultadoTiempo sabado = calcular(abierta(d(2026, 10, 16)), a(2026, 10, 17));
        assertThat(sabado.estado()).isEqualTo(EstadoTiempo.VENCIDA);
        assertThat(sabado.diasRetrasoHabiles()).isZero();
    }

    // ───────────────────────── en pausa ─────────────────────────

    @Test
    @DisplayName("EN_PAUSA: con una pausa abierta, el estado es en pausa aunque el compromiso ya haya pasado")
    void enPausa() {
        EntradaTiempo e = new EntradaTiempo(EstadoServicio.PENDIENTE, d(2026, 9, 23), d(2026, 9, 23), null, List.of(new Lapso(d(2026, 9, 16), null)), null);
        ResultadoTiempo r = calcular(e, a(2026, 10, 19));
        assertThat(r.estado()).isEqualTo(EstadoTiempo.EN_PAUSA);
        assertThat(r.diasRetrasoHabiles()).isZero();
        assertThat(r.diasPausaHabiles()).isEqualTo(22);   // 16-sep (incluido) a 19-oct (excluido): 22 días hábiles, sin el festivo del 12-oct
        assertThat(r.fechaCompromisoAjustada()).isEqualTo(calendario().sumarDiasHabiles(d(2026, 9, 23), 22));
    }

    @Test
    @DisplayName("Una pausa de 3 días hábiles corre la fecha de compromiso 3 días hábiles")
    void pausaCorreLaFecha() {
        // pausa del lunes 28-sep (incluido) al jueves 1-oct (excluido): 28, 29 y 30 = 3 días hábiles
        EntradaTiempo e = new EntradaTiempo(EstadoServicio.EN_EJECUCION, d(2026, 9, 23), d(2026, 9, 23), null, List.of(new Lapso(d(2026, 9, 28), d(2026, 10, 1))), null);
        ResultadoTiempo r = calcular(e, a(2026, 10, 2));
        assertThat(r.diasPausaHabiles()).isEqualTo(3);
        assertThat(r.fechaCompromisoAjustada()).isEqualTo(d(2026, 9, 28));      // 23-sep + 3 días hábiles = 24, 25 y 28
        assertThat(r.estado()).isEqualTo(EstadoTiempo.VENCIDA);
        assertThat(r.diasRetrasoHabiles()).isEqualTo(4);                          // 29, 30, 1 y 2
    }

    @Test
    @DisplayName("Una pausa que empieza y termina el mismo día vale 0, y una que cruza un fin de semana solo cuenta los días hábiles")
    void pausasCortas() {
        assertThat(calculador.diasDePausa(d(2026, 9, 28), d(2026, 9, 28))).isZero();
        assertThat(calculador.diasDePausa(d(2026, 9, 25), d(2026, 9, 28))).isEqualTo(1);    // viernes (incluido) a lunes (excluido): solo el viernes
        assertThat(calculador.diasDePausa(d(2026, 9, 25), d(2026, 9, 29))).isEqualTo(2);
    }

    @Test
    @DisplayName("Varias pausas se suman")
    void variasPausas() {
        EntradaTiempo e = new EntradaTiempo(EstadoServicio.EN_EJECUCION, d(2026, 9, 23), d(2026, 9, 23), null,
                List.of(new Lapso(d(2026, 9, 16), d(2026, 9, 18)), new Lapso(d(2026, 9, 21), d(2026, 9, 22))), null);
        assertThat(calcular(e, a(2026, 9, 17)).diasPausaHabiles()).isEqualTo(3);
    }

    // ───────────────────────── entregada ─────────────────────────

    @Test
    @DisplayName("Publicada a tiempo: el día de compromiso, o antes")
    void publicadaATiempo() {
        EntradaTiempo e = new EntradaTiempo(EstadoServicio.PUBLICADO, d(2026, 9, 23), d(2026, 9, 23), null, List.of(), d(2026, 9, 23));
        ResultadoTiempo r = calcular(e, a(2026, 10, 19));
        assertThat(r.estado()).isEqualTo(EstadoTiempo.ENTREGADA_A_TIEMPO);
        assertThat(r.diasRetrasoHabiles()).isZero();
    }

    @Test
    @DisplayName("Publicada tarde: congela el retraso en días hábiles (solicitud #12: compromiso 1-sep, publicada 25-sep = 18)")
    void publicadaTarde() {
        EntradaTiempo e = new EntradaTiempo(EstadoServicio.PUBLICADO, d(2026, 9, 1), d(2026, 9, 1), null, List.of(), d(2026, 9, 25));
        ResultadoTiempo r = calcular(e, a(2026, 12, 1));      // el resultado no depende de "hoy": no se recalcula
        assertThat(r.estado()).isEqualTo(EstadoTiempo.ENTREGADA_TARDE);
        assertThat(r.diasRetrasoHabiles()).isEqualTo(18);
        assertThat(calcular(e, a(2027, 3, 1)).diasRetrasoHabiles()).isEqualTo(18);
    }

    @Test
    @DisplayName("Una pausa corre el compromiso también al medir la entrega: publicada dentro del plazo ajustado es a tiempo")
    void publicadaConPausa() {
        EntradaTiempo e = new EntradaTiempo(EstadoServicio.PUBLICADO, d(2026, 9, 23), d(2026, 9, 23), null,
                List.of(new Lapso(d(2026, 9, 28), d(2026, 10, 1))), d(2026, 9, 28));
        assertThat(calcular(e, a(2026, 10, 19)).estado()).isEqualTo(EstadoTiempo.ENTREGADA_A_TIEMPO);   // ajustado = 28-sep
    }

    // ───────────────────────── reprogramación ─────────────────────────

    private static EntradaTiempo reprogramada(ImputableA causa) {
        // original 23-sep; reprogramada al 30-sep
        return new EntradaTiempo(EstadoServicio.REPROGRAMADO, d(2026, 9, 30), d(2026, 9, 23), causa, List.of(), null);
    }

    @Test
    @DisplayName("Reprogramación por causa de Polygraph: se mide contra la fecha ORIGINAL")
    void reprogramacionPorPolygraph() {
        ResultadoTiempo r = calcular(reprogramada(ImputableA.POLYGRAPH), a(2026, 10, 2));
        assertThat(r.fechaCompromisoAjustada()).isEqualTo(d(2026, 9, 23));
        assertThat(r.estado()).isEqualTo(EstadoTiempo.VENCIDA);
        assertThat(r.diasRetrasoHabiles()).isEqualTo(7);         // 24, 25, 28, 29, 30, 1 y 2
    }

    @Test
    @DisplayName("Reprogramación por causa del cliente o del candidato: se mide contra la fecha NUEVA")
    void reprogramacionPorClienteOCandidato() {
        for (ImputableA causa : List.of(ImputableA.CLIENTE, ImputableA.CANDIDATO)) {
            ResultadoTiempo r = calcular(reprogramada(causa), a(2026, 10, 2));
            assertThat(r.fechaCompromisoAjustada()).as(causa.name()).isEqualTo(d(2026, 9, 30));
            assertThat(r.diasRetrasoHabiles()).as(causa.name()).isEqualTo(2);                 // 1 y 2
        }
    }

    @Test
    @DisplayName("Si el parámetro dice VIGENTE, la causa de Polygraph también se mide contra la nueva fecha")
    void parametroVigente() {
        var porVigente = new SemaforoTiempoCalculator(calendario(), new ParametrosTiempo(BOGOTA, java.time.LocalTime.of(16, 0), 1, false));
        ResultadoTiempo r = porVigente.calcular(reprogramada(ImputableA.POLYGRAPH), a(2026, 10, 2));
        assertThat(r.fechaCompromisoAjustada()).isEqualTo(d(2026, 9, 30));
        assertThat(r.diasRetrasoHabiles()).isEqualTo(2);
    }

    // ───────────────────────── cancelada y borde ─────────────────────────

    @Test
    @DisplayName("Cancelada: sin estado de tiempo")
    void cancelada() {
        EntradaTiempo e = new EntradaTiempo(EstadoServicio.CANCELADO, d(2026, 9, 1), d(2026, 9, 1), null, List.of(), null);
        assertThat(calcular(e, a(2026, 10, 19)).estado()).isNull();
    }

    @Test
    @DisplayName("La hora se mira en Bogotá: a las 21:00 UTC del 23-sep (16:00 en Bogotá) sigue siendo el día de compromiso")
    void zonaHoraria() {
        ZonedDateTime enUtc = ZonedDateTime.of(2026, 9, 24, 3, 0, 0, 0, java.time.ZoneOffset.UTC);   // 22:00 del 23-sep en Bogotá
        assertThat(calcular(abierta(d(2026, 9, 23)), enUtc).estado()).isEqualTo(EstadoTiempo.POR_VENCER);
    }
}
