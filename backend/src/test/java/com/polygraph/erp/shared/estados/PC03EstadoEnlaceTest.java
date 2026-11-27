package com.polygraph.erp.shared.estados;

import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.estados.EstadoEnlaceCandidato.Datos;
import com.polygraph.erp.shared.estados.EstadoEnlaceCandidato.Estado;
import com.polygraph.erp.shared.estados.ProximaAccion.Accion;
import com.polygraph.erp.shared.estados.ProximaAccion.Sub;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.utils.FechasLegibles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** PC-03: un solo estado del enlace con su texto, sin frases contradictorias; la próxima acción mientras se espera al candidato; un cancelado sin compromiso. */
@DisplayName("PC-03 — estado del enlace del candidato")
class PC03EstadoEnlaceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 25);

    private static Datos datos(LocalDateTime enviado, LocalDateTime abierto, LocalDate vence) {
        return new Datos(enviado, abierto, false, null, 2, 3, vence, null);
    }

    @Test
    @DisplayName("Las fechas de las frases salen cortas y en español: «24 de nov»")
    void fechaCorta() {
        assertThat(FechasLegibles.corta(LocalDate.of(2026, 11, 24))).isEqualTo("24 de nov");
        assertThat(FechasLegibles.corta(LocalDate.of(2026, 10, 3))).isEqualTo("3 de oct");
        assertThat(FechasLegibles.corta(LocalDate.of(2026, 1, 1))).isEqualTo("1 de ene");
        assertThat(FechasLegibles.corta(null)).isEqualTo("sin fecha");
    }

    @Test
    @DisplayName("NO_ENVIADO: Polygraph todavía no lo envió y no se habla de vigencia")
    void noEnviado() {
        var r = EstadoEnlaceCandidato.calcular(datos(null, null, LocalDate.of(2026, 10, 23)), HOY);
        assertThat(r.estado()).isEqualTo(Estado.NO_ENVIADO);
        assertThat(r.texto()).isEqualTo("Polygraph todavía no ha enviado el enlace al candidato.");
    }

    @Test
    @DisplayName("ENVIADO: fecha del envío, todavía no lo abre y hasta cuándo vale")
    void enviado() {
        var r = EstadoEnlaceCandidato.calcular(datos(LocalDateTime.of(2026, 11, 24, 10, 0), null, LocalDate.of(2026, 11, 26)), HOY);
        assertThat(r.estado()).isEqualTo(Estado.ENVIADO);
        assertThat(r.texto()).isEqualTo("Polygraph le envió el enlace el 24 de nov. Todavía no lo ha abierto. Vence el 26 de nov.");
    }

    @Test
    @DisplayName("ABIERTO: lo abrió con su cédula y lleva x de y secciones")
    void abierto() {
        var r = EstadoEnlaceCandidato.calcular(datos(LocalDateTime.of(2026, 11, 24, 10, 0), LocalDateTime.of(2026, 11, 24, 12, 0), LocalDate.of(2026, 11, 26)), HOY);
        assertThat(r.estado()).isEqualTo(Estado.ABIERTO);
        assertThat(r.texto()).isEqualTo("Lo abrió con su cédula y lleva 2 de 3 secciones. Vence el 26 de nov.");
    }

    @Test
    @DisplayName("#37 · Nunca dice a la vez «aún no ha enviado» y «lo abrió»: con la vigencia ya pasada es VENCIDO y pide reenviar")
    void caso37NoSeContradice() {
        // #37: sin primer envío registrado, abierto el 21/10, vence el 23/10.
        var r = EstadoEnlaceCandidato.calcular(datos(null, LocalDateTime.of(2026, 10, 21, 9, 0), LocalDate.of(2026, 10, 23)), HOY);
        assertThat(r.estado()).isEqualTo(Estado.VENCIDO);
        assertThat(r.texto()).isEqualTo("El enlace venció el 23 de oct. Tu gestor debe reenviarlo.");
        assertThat(r.texto()).doesNotContain("no ha enviado").doesNotContain("vence el 23");
        // Sin primer envío pero abierto y vigente: ABIERTO, no NO_ENVIADO.
        var vigente = EstadoEnlaceCandidato.calcular(datos(null, LocalDateTime.of(2026, 11, 24, 9, 0), LocalDate.of(2026, 11, 26)), HOY);
        assertThat(vigente.estado()).isEqualTo(Estado.ABIERTO);
        assertThat(vigente.texto()).doesNotContain("no ha enviado");
    }

    @Test
    @DisplayName("Si la vigencia es anterior a hoy, SIEMPRE es VENCIDO (aunque ya lo haya abierto); la fecha de hoy todavía vale")
    void vencidoSiempre() {
        assertThat(EstadoEnlaceCandidato.calcular(datos(LocalDateTime.of(2026, 11, 2, 10, 0), null, LocalDate.of(2026, 11, 5)), HOY).estado()).isEqualTo(Estado.VENCIDO);
        assertThat(EstadoEnlaceCandidato.calcular(datos(LocalDateTime.of(2026, 11, 2, 10, 0), LocalDateTime.of(2026, 11, 3, 9, 0), LocalDate.of(2026, 11, 5)), HOY).estado()).isEqualTo(Estado.VENCIDO);
        assertThat(EstadoEnlaceCandidato.calcular(datos(LocalDateTime.of(2026, 11, 23, 10, 0), null, HOY), HOY).estado()).isEqualTo(Estado.ENVIADO);
    }

    @Test
    @DisplayName("El recordatorio automático se agrega cuando existe")
    void recordatorio() {
        var d = new Datos(LocalDateTime.of(2026, 11, 23, 10, 0), null, false, null, 0, 3, LocalDate.of(2026, 11, 26), LocalDateTime.of(2026, 11, 24, 10, 0));
        assertThat(EstadoEnlaceCandidato.calcular(d, HOY).texto()).endsWith("Vence el 26 de nov. Recordatorio automático enviado el 24 de nov.");
        var vencido = new Datos(LocalDateTime.of(2026, 10, 20, 10, 0), null, false, null, 0, 3, LocalDate.of(2026, 10, 23), LocalDateTime.of(2026, 10, 21, 10, 0));
        assertThat(EstadoEnlaceCandidato.calcular(vencido, HOY).texto()).isEqualTo("El enlace venció el 23 de oct. Tu gestor debe reenviarlo. Recordatorio automático enviado el 21 de oct.");
    }

    @Test
    @DisplayName("ENVIADO_POR_CANDIDATO: el candidato ya envió su información")
    void enviadoPorCandidato() {
        var d = new Datos(LocalDateTime.of(2026, 11, 20, 10, 0), LocalDateTime.of(2026, 11, 20, 11, 0), true, LocalDateTime.of(2026, 11, 22, 8, 0), 3, 3, LocalDate.of(2026, 11, 24), null);
        var r = EstadoEnlaceCandidato.calcular(d, HOY);
        assertThat(r.estado()).isEqualTo(Estado.ENVIADO_POR_CANDIDATO);
        assertThat(r.texto()).isEqualTo("El candidato envió su información el 22 de nov.");   // ni vencido ni «vence el»
    }

    // ───────────── próxima acción mientras se espera al candidato ─────────────

    @Test
    @DisplayName("Un servicio en pausa por el candidato sin hoja de vida (nunca abrió el enlace) también «se espera»: la próxima acción es Enviar o Reenviar enlace, no Asignar")
    void proximaAccionEnPausaPorCandidato() {
        List<Sub> pendientes = List.of(new Sub(EstadoSubproceso.PENDIENTE, true, false));
        Boolean esperando = ProximaAccion.esperandoAlCandidato(null, true);
        assertThat(esperando).isFalse();
        assertThat(ProximaAccion.de(false, pendientes, false, esperando, false)).isEqualTo(Accion.ENVIAR_ENLACE);
        assertThat(ProximaAccion.de(false, pendientes, false, esperando, true)).isEqualTo(Accion.REENVIAR_ENLACE);
        // Sin pausa por el candidato y sin hoja de vida registrada, todo sigue igual que antes: se asigna.
        assertThat(ProximaAccion.esperandoAlCandidato(null, false)).isNull();
        assertThat(ProximaAccion.de(false, pendientes, false, ProximaAccion.esperandoAlCandidato(null, false), false)).isEqualTo(Accion.ASIGNAR);
        // Una hoja de vida ya enviada no se pisa, ni siquiera con la pausa abierta.
        assertThat(ProximaAccion.esperandoAlCandidato(true, true)).isTrue();
        assertThat(ProximaAccion.esperandoAlCandidato(false, false)).isFalse();
    }

    // ───────────── compromiso de un cancelado ─────────────

    @Test
    @DisplayName("Un servicio cancelado no tiene fecha de compromiso («—» en pantalla); uno abierto muestra la ajustada o la estimada")
    void canceladoSinCompromiso() {
        Servicio s = Servicio.builder().estado(EstadoServicio.PENDIENTE).fechaEntregaEstimada(LocalDate.of(2026, 11, 18)).build();
        assertThat(s.fechaParaMostrar()).isEqualTo(LocalDate.of(2026, 11, 18));
        s.setFechaCompromisoAjustada(LocalDate.of(2026, 11, 20));
        assertThat(s.fechaParaMostrar()).isEqualTo(LocalDate.of(2026, 11, 20));
        s.setEstado(EstadoServicio.CANCELADO);
        assertThat(s.fechaParaMostrar()).isNull();
    }
}
