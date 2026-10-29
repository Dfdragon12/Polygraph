package com.polygraph.erp.shared.estados;

import com.polygraph.erp.shared.enums.EstadoSubproceso;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.polygraph.erp.shared.enums.EstadoSubproceso.*;
import static org.junit.jupiter.api.Assertions.*;

class ReglasCitaTest {

    private static final LocalDateTime CITA = LocalDateTime.of(2026, 10, 27, 9, 0);

    private static LocalDateTime a(int h, int m) { return LocalDateTime.of(2026, 10, 27, h, m); }

    @Test
    void laToleranciaEsDeQuinceMinutosDespuesDeLaHora() {
        assertEquals(15, ReglasCita.TOLERANCIA_MINUTOS);
        assertFalse(ReglasCita.pasoLaTolerancia(CITA, a(9, 14), 15));
        assertTrue(ReglasCita.pasoLaTolerancia(CITA, a(9, 15), 15));
        assertTrue(ReglasCita.pasoLaTolerancia(CITA, a(11, 0), 15));
        assertFalse(ReglasCita.pasoLaTolerancia(null, a(11, 0), 15));
    }

    @Test
    void noHaLlegadoSoloDuranteElDiaYYaPasadaLaHora() {
        assertFalse(ReglasCita.puedeAvisarQueNoHaLlegado(true, EN_EJECUCION, false, false, CITA, a(8, 59)));   // antes de la hora
        assertTrue(ReglasCita.puedeAvisarQueNoHaLlegado(true, EN_EJECUCION, false, false, CITA, a(9, 0)));
        assertTrue(ReglasCita.puedeAvisarQueNoHaLlegado(true, EN_EJECUCION, false, false, CITA, a(9, 40)));
        assertFalse(ReglasCita.puedeAvisarQueNoHaLlegado(true, EN_EJECUCION, false, false, CITA, CITA.plusDays(1))); // otro día
    }

    @Test
    void noHaLlegadoSeAvisaUnaSolaVezYSoloSiNoSeInicioElInforme() {
        assertFalse(ReglasCita.puedeAvisarQueNoHaLlegado(true, EN_EJECUCION, false, true, CITA, a(9, 20)));    // ya avisó
        assertFalse(ReglasCita.puedeAvisarQueNoHaLlegado(true, EN_EJECUCION, true, false, CITA, a(9, 20)));    // informe iniciado
        assertFalse(ReglasCita.puedeAvisarQueNoHaLlegado(false, EN_EJECUCION, false, false, CITA, a(9, 20)));  // no es cita
    }

    @Test
    void noSePresentoSoloDespuesDeLosQuinceMinutos() {
        assertFalse(ReglasCita.puedeRegistrarNoSePresento(true, EN_EJECUCION, false, CITA, a(9, 14), 15));
        assertTrue(ReglasCita.puedeRegistrarNoSePresento(true, EN_EJECUCION, false, CITA, a(9, 15), 15));
    }

    @Test
    void soloUnaCitaEnEjecucionSinInformeIniciadoSePuedeDarPorNoRealizada() {
        for (EstadoSubproceso e : new EstadoSubproceso[]{PENDIENTE, PROGRAMANDO, REPROGRAMADO, FINALIZADO, PUBLICADO, CANCELADO}) {
            assertFalse(ReglasCita.puedeRegistrarNoSePresento(true, e, false, CITA, a(10, 0), 15), e.name());
        }
        assertFalse(ReglasCita.puedeRegistrarNoSePresento(true, EN_EJECUCION, true, CITA, a(10, 0), 15));      // ya inició el informe
        assertFalse(ReglasCita.puedeRegistrarNoSePresento(false, EN_EJECUCION, false, CITA, a(10, 0), 15));    // un análisis no tiene cita
        assertFalse(ReglasCita.puedeRegistrarNoSePresento(true, EN_EJECUCION, false, null, a(10, 0), 15));     // sin fecha
    }

    @Test
    void porConfirmarEsUnaCitaVencidaSinRegistroPeroNoUnaQueSigueDentroDeLaTolerancia() {
        assertFalse(ReglasCita.porConfirmar(true, EN_EJECUCION, false, CITA, a(9, 10), 15));
        assertTrue(ReglasCita.porConfirmar(true, EN_EJECUCION, false, CITA, a(9, 16), 15));
        assertFalse(ReglasCita.porConfirmar(true, EN_EJECUCION, true, CITA, a(9, 16), 15));    // el profesional ya inició el informe
        assertFalse(ReglasCita.porConfirmar(true, REPROGRAMADO, false, CITA, a(9, 16), 15));   // ya se registró que no se realizó
    }
}
