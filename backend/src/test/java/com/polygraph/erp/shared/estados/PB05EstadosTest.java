package com.polygraph.erp.shared.estados;

import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.estados.AnillosRiesgo.Anillos;
import com.polygraph.erp.shared.estados.ProximaAccion.Accion;
import com.polygraph.erp.shared.estados.ProximaAccion.ResponsableRetraso;
import com.polygraph.erp.shared.estados.ProximaAccion.Sub;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas puras de PB-05: grupos de tiempo, anillos de riesgo, periodos y la «próxima acción» del gestor. */
@DisplayName("PB-05 — reglas de las pantallas de inicio y de Solicitudes")
class PB05EstadosTest {

    // ───────────────────────── grupos de tiempo ─────────────────────────

    @Test
    @DisplayName("Los cinco grupos de tiempo salen de los días hábiles que quedan; entregadas y canceladas no están en ninguno")
    void gruposDeTiempo() {
        assertThat(BucketTiempo.de(EstadoTiempo.VENCIDA, -3)).isEqualTo(BucketTiempo.VENCIDAS);
        assertThat(BucketTiempo.de(EstadoTiempo.POR_VENCER, 0)).isEqualTo(BucketTiempo.VENCE_HOY);
        assertThat(BucketTiempo.de(EstadoTiempo.POR_VENCER, null)).isEqualTo(BucketTiempo.VENCE_HOY);
        assertThat(BucketTiempo.de(EstadoTiempo.POR_VENCER, 1)).isEqualTo(BucketTiempo.VENCE_MANANA);
        assertThat(BucketTiempo.de(EstadoTiempo.POR_VENCER, 2)).isEqualTo(BucketTiempo.A_TIEMPO);
        assertThat(BucketTiempo.de(EstadoTiempo.A_TIEMPO, 9)).isEqualTo(BucketTiempo.A_TIEMPO);
        assertThat(BucketTiempo.de(EstadoTiempo.EN_PAUSA, null)).isEqualTo(BucketTiempo.EN_PAUSA);
        assertThat(BucketTiempo.de(EstadoTiempo.ENTREGADA_A_TIEMPO, 0)).isNull();
        assertThat(BucketTiempo.de(EstadoTiempo.ENTREGADA_TARDE, 0)).isNull();
        assertThat(BucketTiempo.de(null, 0)).isNull();
    }

    // ───────────────────────── anillos ─────────────────────────

    @Test
    @DisplayName("Los tres porcentajes suman SIEMPRE 100, también cuando el redondeo simple daría 99 o 101")
    void anillosSuman100() {
        for (long b = 0; b <= 12; b++) {
            for (long m = 0; m <= 12; m++) {
                for (long a = 0; a <= 12; a++) {
                    if (b + m + a == 0) continue;
                    Anillos r = AnillosRiesgo.de(b, m, a);
                    assertThat(r.pctBajo() + r.pctMedio() + r.pctAlto()).as("%d/%d/%d", b, m, a).isEqualTo(100);
                    assertThat(r.pctBajo()).isBetween(0, 100);
                    assertThat(r.total()).isEqualTo((int) (b + m + a));
                }
            }
        }
    }

    @Test
    @DisplayName("Ejemplo del diseño del cliente: 14 / 4 / 2 de 20 = 70 / 20 / 10 %; y 8 / 2 / 2 de 12 = 67 / 17 / 16 %")
    void anillosDelDiseno() {
        Anillos cliente = AnillosRiesgo.de(14, 4, 2);
        assertThat(List.of(cliente.pctBajo(), cliente.pctMedio(), cliente.pctAlto())).containsExactly(70, 20, 10);
        assertThat(cliente.total()).isEqualTo(20);
        Anillos gestor = AnillosRiesgo.de(8, 2, 2);
        assertThat(List.of(gestor.pctBajo(), gestor.pctMedio(), gestor.pctAlto())).containsExactly(67, 17, 16);
    }

    @Test
    @DisplayName("Sin estudios con resultado, todo en cero (la pantalla dice «Todavía no hay estudios con resultado en este periodo»)")
    void anillosSinEstudios() {
        Anillos r = AnillosRiesgo.de(0, 0, 0);
        assertThat(r.total()).isZero();
        assertThat(r.pctBajo() + r.pctMedio() + r.pctAlto()).isZero();
    }

    @Test
    @DisplayName("Un nivel sin estudios queda en 0 % aunque otro tenga residuo")
    void nivelSinEstudiosNoRecibePunto() {
        Anillos r = AnillosRiesgo.de(1, 2, 0);
        assertThat(r.pctAlto()).isZero();
        assertThat(r.pctBajo() + r.pctMedio()).isEqualTo(100);
    }

    // ───────────────────────── periodos ─────────────────────────

    @Test
    @DisplayName("Periodos del selector: Hoy, Última semana (7 días), Este mes, Último año (12 meses) y Total sin límite")
    void periodos() {
        LocalDate hoy = LocalDate.of(2026, 11, 10);
        assertThat(PeriodoEstadistica.HOY.desde(hoy)).isEqualTo(hoy);
        assertThat(PeriodoEstadistica.SEMANA.desde(hoy)).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(PeriodoEstadistica.MES.desde(hoy)).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(PeriodoEstadistica.ANIO.desde(hoy)).isEqualTo(LocalDate.of(2025, 11, 11));
        assertThat(PeriodoEstadistica.TOTAL.desde(hoy)).isNull();
        assertThat(PeriodoEstadistica.de("anio")).isEqualTo(PeriodoEstadistica.ANIO);
        assertThat(PeriodoEstadistica.de(null)).isEqualTo(PeriodoEstadistica.ANIO);
        assertThatThrownBy(() -> PeriodoEstadistica.de("decada")).hasMessageContaining("Periodo desconocido");
    }

    // ───────────────────────── próxima acción ─────────────────────────

    private static Sub sp(EstadoSubproceso e) { return new Sub(e, false, true); }

    @Test
    @DisplayName("Con un subproceso Finalizado, la próxima acción es revisarlo (lo único que pide acción del gestor)")
    void revisar() {
        assertThat(ProximaAccion.de(false, List.of(sp(EstadoSubproceso.FINALIZADO), sp(EstadoSubproceso.EN_EJECUCION)), false, true, true)).isEqualTo(Accion.REVISAR);
        assertThat(ProximaAccion.de(false, List.of(sp(EstadoSubproceso.FINALIZADO)), false, false, false)).isEqualTo(Accion.REVISAR);   // pasa por delante del enlace
    }

    @Test
    @DisplayName("Todo publicado: emitir la pasta final; con la pasta emitida, ver el expediente")
    void pasta() {
        assertThat(ProximaAccion.de(false, List.of(sp(EstadoSubproceso.PUBLICADO)), false, true, true)).isEqualTo(Accion.EMITIR_PASTA);
        assertThat(ProximaAccion.de(false, List.of(sp(EstadoSubproceso.PUBLICADO)), true, true, true)).isEqualTo(Accion.VER_EXPEDIENTE);
        // los cancelados no cuentan para «todo publicado»
        assertThat(ProximaAccion.de(false, List.of(sp(EstadoSubproceso.PUBLICADO), sp(EstadoSubproceso.CANCELADO)), false, true, true)).isEqualTo(Accion.EMITIR_PASTA);
    }

    @Test
    @DisplayName("Candidato sin terminar: Enviar enlace si aún no se envió, Reenviar enlace si ya se envió")
    void enlace() {
        List<Sub> pendientes = List.of(sp(EstadoSubproceso.PENDIENTE), sp(EstadoSubproceso.PENDIENTE));
        assertThat(ProximaAccion.de(false, pendientes, false, false, false)).isEqualTo(Accion.ENVIAR_ENLACE);
        assertThat(ProximaAccion.de(false, pendientes, false, false, true)).isEqualTo(Accion.REENVIAR_ENLACE);
        // sin candidato o con la hoja de vida enviada no aplica
        assertThat(ProximaAccion.de(false, List.of(new Sub(EstadoSubproceso.PENDIENTE, false, false)), false, true, false)).isEqualTo(Accion.ASIGNAR);
    }

    @Test
    @DisplayName("Asignar (pendiente sin responsable), Avisar al programador (cita en Programando) y Ver avance")
    void asignarAvisarVer() {
        assertThat(ProximaAccion.de(false, List.of(new Sub(EstadoSubproceso.PENDIENTE, true, false)), false, true, true)).isEqualTo(Accion.ASIGNAR);
        assertThat(ProximaAccion.de(false, List.of(new Sub(EstadoSubproceso.PROGRAMANDO, true, true)), false, true, true)).isEqualTo(Accion.AVISAR_PROGRAMADOR);
        assertThat(ProximaAccion.de(false, List.of(sp(EstadoSubproceso.EN_EJECUCION)), false, true, true)).isEqualTo(Accion.VER_AVANCE);
        assertThat(ProximaAccion.de(true, List.of(sp(EstadoSubproceso.FINALIZADO)), false, true, true)).isEqualTo(Accion.VER_AVANCE);
        assertThat(ProximaAccion.de(false, List.of(), false, true, true)).isEqualTo(Accion.VER_AVANCE);
    }

    @Test
    @DisplayName("Los textos de los botones son los del diseño")
    void etiquetas() {
        assertThat(Accion.REVISAR.etiqueta()).isEqualTo("Revisar informe");
        assertThat(Accion.EMITIR_PASTA.etiqueta()).isEqualTo("Emitir pasta final");
        assertThat(Accion.ENVIAR_ENLACE.etiqueta()).isEqualTo("Enviar enlace");
        assertThat(Accion.REENVIAR_ENLACE.etiqueta()).isEqualTo("Reenviar enlace");
        assertThat(Accion.AVISAR_PROGRAMADOR.etiqueta()).isEqualTo("Avisar al programador");
        assertThat(Accion.VER_EXPEDIENTE.etiqueta()).isEqualTo("Ver expediente");
        assertThat(Accion.VER_AVANCE.etiqueta()).isEqualTo("Ver avance");
    }

    // ───────────────────────── responsable del retraso ─────────────────────────

    @Test
    @DisplayName("Una vencida es de la revisión del gestor, del candidato o del campo, y se cuenta en UNA sola causa")
    void responsableDelRetraso() {
        assertThat(ProximaAccion.responsableDelRetraso(List.of(EstadoSubproceso.FINALIZADO, EstadoSubproceso.EN_EJECUCION), false, false)).isEqualTo(ResponsableRetraso.REVISION_GESTOR);
        assertThat(ProximaAccion.responsableDelRetraso(List.of(EstadoSubproceso.PUBLICADO), false, true)).isEqualTo(ResponsableRetraso.REVISION_GESTOR);   // falta emitir la pasta
        assertThat(ProximaAccion.responsableDelRetraso(List.of(EstadoSubproceso.PENDIENTE), false, false)).isEqualTo(ResponsableRetraso.CANDIDATO);
        assertThat(ProximaAccion.responsableDelRetraso(List.of(EstadoSubproceso.EN_EJECUCION, EstadoSubproceso.PENDIENTE), false, true)).isEqualTo(ResponsableRetraso.CAMPO);
        assertThat(ProximaAccion.responsableDelRetraso(List.of(), false, null)).isEqualTo(ResponsableRetraso.CAMPO);
    }

    @Test
    @DisplayName("«Pendiente del gestor»: algo Finalizado, o todo publicado sin pasta emitida")
    void pendienteDelGestor() {
        assertThat(ProximaAccion.pendienteDelGestor(List.of(EstadoSubproceso.FINALIZADO), false)).isTrue();
        assertThat(ProximaAccion.pendienteDelGestor(List.of(EstadoSubproceso.PUBLICADO, EstadoSubproceso.PUBLICADO), false)).isTrue();
        assertThat(ProximaAccion.pendienteDelGestor(List.of(EstadoSubproceso.PUBLICADO, EstadoSubproceso.PUBLICADO), true)).isFalse();
        assertThat(ProximaAccion.pendienteDelGestor(List.of(EstadoSubproceso.PUBLICADO, EstadoSubproceso.EN_EJECUCION), false)).isFalse();
        assertThat(ProximaAccion.pendienteDelGestor(List.of(EstadoSubproceso.CANCELADO), false)).isFalse();
    }
}
