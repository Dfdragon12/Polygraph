package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** PC-05 (PB-H-15): la segunda línea de «Qué llegó» en Control de calidad no repite la primera. */
@DisplayName("PC-05 — Control de calidad: «Qué llegó» en una sola línea")
class PC05TextosTest {

    private static ServicioSubproceso sp(EstadoSubproceso estado) {
        return ServicioSubproceso.builder().estado(estado).build();
    }

    @Test
    @DisplayName("Un estudio de un solo subproceso que se llama igual que el estudio: no hay segunda línea (antes decía «Antecedentes · Finalizado 1» debajo de «Antecedentes · Finalizado»)")
    void sinRepetir() {
        assertThat(BandejaGestorService.detalleQueLlego("Antecedentes", "Antecedentes", List.of(sp(EstadoSubproceso.FINALIZADO)))).isNull();
        assertThat(BandejaGestorService.detalleQueLlego(" antecedentes ", "Antecedentes", List.of(sp(EstadoSubproceso.FINALIZADO)))).isNull();
    }

    @Test
    @DisplayName("Si el estudio se llama distinto del subproceso, la segunda línea dice a qué estudio pertenece")
    void nombraElEstudio() {
        assertThat(BandejaGestorService.detalleQueLlego("Estudio Básico", "Antecedentes", List.of(sp(EstadoSubproceso.FINALIZADO)))).isEqualTo("Estudio Básico");
    }

    @Test
    @DisplayName("Con varios subprocesos dice cómo van los demás; un subproceso cancelado no cuenta como «otro»")
    void conVariosSubprocesos() {
        var uno = List.of(sp(EstadoSubproceso.FINALIZADO), sp(EstadoSubproceso.CANCELADO));
        assertThat(BandejaGestorService.detalleQueLlego("Antecedentes", "Antecedentes", uno)).isNull();

        var dos = List.of(sp(EstadoSubproceso.FINALIZADO), sp(EstadoSubproceso.PUBLICADO));
        String detalle = BandejaGestorService.detalleQueLlego("Estudio Básico", "Antecedentes", dos);
        assertThat(detalle).startsWith("Estudio Básico · ").contains("Finalizado 1").contains("Publicado 1");
    }

    @Test
    @DisplayName("Sin nombre de estudio no rompe: solo queda lo de los demás subprocesos, o nada")
    void sinProceso() {
        assertThat(BandejaGestorService.detalleQueLlego(null, "Antecedentes", List.of(sp(EstadoSubproceso.FINALIZADO)))).isNull();
        assertThat(BandejaGestorService.detalleQueLlego("", "Antecedentes", List.of(sp(EstadoSubproceso.FINALIZADO), sp(EstadoSubproceso.EN_EJECUCION))))
                .contains("Finalizado 1").contains("En ejecución 1");
    }
}
