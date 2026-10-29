package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.shared.enums.TipoTrabajo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecalculoTiempoProcesador — el trabajo de la cola")
class RecalculoTiempoProcesadorTest {

    @Mock private SemaforoTiempoService semaforo;
    @InjectMocks private RecalculoTiempoProcesador procesador;

    @Test
    @DisplayName("Es el procesador del tipo RECALCULO_TIEMPO")
    void tipo() {
        assertThat(procesador.tipo()).isEqualTo(TipoTrabajo.RECALCULO_TIEMPO);
    }

    @Test
    @DisplayName("Sin errores: devuelve el resumen")
    void ok() throws Exception {
        when(semaforo.recalcularEnCurso()).thenReturn(new SemaforoTiempoService.Resumen(10, 3, List.of()));
        var r = procesador.procesar(null, Map.of("motivo", "diario 00:05"), p -> { });
        assertThat(r.datos()).isEqualTo(Map.of("procesadas", 10, "cambiadas", 3, "motivo", "diario 00:05"));
    }

    @Test
    @DisplayName("Con solicitudes que fallaron: el trabajo termina con error (queda visible en Trabajos en segundo plano) con la lista")
    void conErrores() {
        when(semaforo.recalcularEnCurso()).thenReturn(new SemaforoTiempoService.Resumen(10, 3, List.of("Solicitud 7: boom")));
        assertThatThrownBy(() -> procesador.procesar(null, Map.of(), p -> { }))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("1 de 10").hasMessageContaining("Solicitud 7: boom");
    }
}
