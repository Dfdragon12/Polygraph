package com.polygraph.erp.shared.service;

import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.shared.dto.TiempoServicio;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.OrigenPausa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TiempoServicioService — presenta lo que guardó el semáforo de tiempo")
class TiempoServicioServiceTest {

    private TiempoServicioService service;

    @BeforeEach
    void setUp() {
        service = new TiempoServicioService(new DiasHabilesService(new CalendarioHabilService(anio -> FestivosColombiaGenerator.generar(anio).keySet(),
                () -> ParametrosTiempo.POR_DEFECTO, java.time.Clock.systemUTC())));
    }

    private static Servicio guardado(EstadoTiempo estado, Integer retraso, Integer restantes) {
        return Servicio.builder().estadoTiempo(estado).diasRetrasoHabiles(retraso).diasRestantesHabiles(restantes)
                .fechaCompromisoAjustada(LocalDate.of(2026, 9, 23)).build();
    }

    @Test
    @DisplayName("Sin estado guardado (cancelada o aún sin calcular) no hay estado de tiempo")
    void sinEstado() {
        assertThat(service.de(Servicio.builder().build())).isNull();
    }

    @Test
    @DisplayName("Vencida dice los días hábiles de retraso y lleva la fecha de compromiso ajustada")
    void vencida() {
        TiempoServicio t = service.de(guardado(EstadoTiempo.VENCIDA, 17, 0));
        assertThat(t.estadoTiempo()).isEqualTo(EstadoTiempo.VENCIDA);
        assertThat(t.diasRetrasoHabiles()).isEqualTo(17);
        assertThat(t.etiqueta()).isEqualTo("Vencida · 17 días hábiles");
        assertThat(t.fechaCompromisoAjustada()).isEqualTo(LocalDate.of(2026, 9, 23));
        assertThat(service.de(guardado(EstadoTiempo.VENCIDA, 1, 0)).etiqueta()).isEqualTo("Vencida · 1 día hábil");
        assertThat(service.de(guardado(EstadoTiempo.VENCIDA, 0, 0)).etiqueta()).isEqualTo("Vencida");
    }

    @Test
    @DisplayName("Los textos del chip: A tiempo, Vence hoy, Vence mañana, el próximo día hábil (viernes a lunes), en pausa y entregadas")
    void textosDelChip() {
        LocalDate lunes = LocalDate.of(2026, 9, 21);
        assertThat(TiempoServicioService.etiqueta(EstadoTiempo.A_TIEMPO, 7, 0, lunes, null, LocalDate.of(2026, 9, 14))).isEqualTo("A tiempo");
        assertThat(TiempoServicioService.etiqueta(EstadoTiempo.POR_VENCER, 0, 0, lunes, null, lunes)).isEqualTo("Vence hoy");
        assertThat(TiempoServicioService.etiqueta(EstadoTiempo.POR_VENCER, 1, 0, lunes.plusDays(1), null, lunes)).isEqualTo("Vence mañana");
        assertThat(TiempoServicioService.etiqueta(EstadoTiempo.POR_VENCER, 1, 0, lunes, null, LocalDate.of(2026, 9, 18)))
                .isEqualTo("Vence el próximo día hábil");   // viernes con compromiso el lunes
        assertThat(TiempoServicioService.etiqueta(EstadoTiempo.EN_PAUSA, 3, 0, lunes, OrigenPausa.CANDIDATO, lunes)).isEqualTo("En pausa · esperando al candidato");
        assertThat(TiempoServicioService.etiqueta(EstadoTiempo.EN_PAUSA, 3, 0, lunes, OrigenPausa.CLIENTE, lunes)).isEqualTo("En pausa · esperando al cliente");
        assertThat(TiempoServicioService.etiqueta(EstadoTiempo.EN_PAUSA, 3, 0, lunes, OrigenPausa.REPROGRAMACION, lunes)).isEqualTo("En pausa · cita reprogramada");
        assertThat(TiempoServicioService.etiqueta(EstadoTiempo.ENTREGADA_A_TIEMPO, 0, 0, lunes, null, lunes)).isEqualTo("Entregada a tiempo");
        assertThat(TiempoServicioService.etiqueta(EstadoTiempo.ENTREGADA_TARDE, 0, 12, lunes, null, lunes)).isEqualTo("Entregada con 12 días hábiles de retraso");
        assertThat(TiempoServicioService.etiqueta(EstadoTiempo.ENTREGADA_TARDE, 0, 1, lunes, null, lunes)).isEqualTo("Entregada con 1 día hábil de retraso");
    }

    @Test
    @DisplayName("El bloque trae todo: recepción, plazo, compromiso vigente y original, pausas, cumplimiento y entrega real")
    void bloqueCompleto() {
        Servicio s = Servicio.builder().estadoTiempo(EstadoTiempo.ENTREGADA_TARDE).diasRetrasoHabiles(12).diasRestantesHabiles(0)
                .fechaRecepcionEfectiva(LocalDate.of(2026, 8, 10)).plazoDiasHabiles(5).fechaEntregaEstimada(LocalDate.of(2026, 9, 1))
                .fechaCompromisoOriginal(LocalDate.of(2026, 8, 17)).fechaCompromisoAjustada(LocalDate.of(2026, 9, 1)).diasPausaHabiles(2)
                .cumplimiento(com.polygraph.erp.shared.enums.Cumplimiento.TARDE).fechaEntregaReal(LocalDate.of(2026, 9, 17)).build();
        TiempoServicio t = service.de(s);
        assertThat(t.fechaRecepcionEfectiva()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(t.plazoDiasHabiles()).isEqualTo(5);
        assertThat(t.fechaCompromiso()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(t.fechaCompromisoOriginal()).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(t.diasPausaHabiles()).isEqualTo(2);
        assertThat(t.cumplimiento()).isEqualTo(com.polygraph.erp.shared.enums.Cumplimiento.TARDE);
        assertThat(t.fechaEntregaReal()).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(t.etiqueta()).isEqualTo("Entregada con 12 días hábiles de retraso");
    }

    @Test
    @DisplayName("Al crear: miércoles 16-sep-2026 con plazo 5 → recepción ese día y compromiso el 23-sep; el viernes 18 a las 16:01 → recepción el lunes 21")
    void datosDeCreacion() {
        var normal = service.datosDeCreacion(LocalDate.of(2026, 9, 16).atTime(10, 0), 5);
        assertThat(normal.recepcionEfectiva()).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(normal.plazoDiasHabiles()).isEqualTo(5);
        assertThat(normal.compromiso()).isEqualTo(LocalDate.of(2026, 9, 23));

        var tarde = service.datosDeCreacion(LocalDate.of(2026, 9, 18).atTime(16, 1), 2);
        assertThat(tarde.recepcionEfectiva()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(tarde.compromiso()).isEqualTo(LocalDate.of(2026, 9, 23));

        var puente = service.datosDeCreacion(LocalDate.of(2026, 10, 9).atTime(17, 0), 1);
        assertThat(puente.recepcionEfectiva()).isEqualTo(LocalDate.of(2026, 10, 13));     // el lunes 12 es festivo
        assertThat(puente.compromiso()).isEqualTo(LocalDate.of(2026, 10, 14));
    }
}
