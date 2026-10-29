package com.polygraph.erp.shared.enums;

import com.polygraph.erp.modules.catalogo.dto.EstadoSubprocesoResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EstadoSubprocesoTest {

    @Test
    void elCatalogoDevuelveLosSieteEnOrdenConSuColor() {
        List<EstadoSubprocesoResponse> c = EstadoSubprocesoResponse.catalogo();
        assertEquals(List.of("PENDIENTE", "PROGRAMANDO", "EN_EJECUCION", "REPROGRAMADO", "FINALIZADO", "PUBLICADO", "CANCELADO"),
                c.stream().map(EstadoSubprocesoResponse::codigo).toList());
        assertEquals(List.of("Pendiente", "Programando", "En ejecución", "Reprogramado", "Finalizado", "Publicado", "Cancelado"),
                c.stream().map(EstadoSubprocesoResponse::nombre).toList());
        assertEquals(List.of("#F59E0B", "#0EA5E9", "#6366F1", "#F97316", "#22C55E", "#0D9488", "#9CA3AF"),
                c.stream().map(EstadoSubprocesoResponse::color).toList());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), c.stream().map(EstadoSubprocesoResponse::orden).toList());
        assertEquals("Realizado, en control de calidad y auditoría", c.get(4).textoCliente());
        assertEquals("Listo: puedes descargar el informe", c.get(5).textoCliente());
    }

    @Test
    void agrupaciones() {
        assertTrue(EstadoSubproceso.PENDIENTE.sinIniciar() && EstadoSubproceso.PROGRAMANDO.sinIniciar());
        assertTrue(EstadoSubproceso.EN_EJECUCION.enCurso() && EstadoSubproceso.REPROGRAMADO.enCurso());
        assertTrue(EstadoSubproceso.FINALIZADO.cerradoParaElResponsable() && EstadoSubproceso.PUBLICADO.cerradoParaElResponsable());
        assertTrue(!EstadoSubproceso.CANCELADO.sinIniciar() && !EstadoSubproceso.CANCELADO.enCurso());
    }

    private static Optional<EstadoSubproceso> m(String viejo, boolean cancelado, boolean cita, boolean prog, boolean prof) {
        return MapeoEstadoSubproceso.desdeAntiguo(viejo, cancelado, cita, prog, prof);
    }

    @Test
    void mapeoDeCadaEstadoViejo() {
        assertEquals(Optional.of(EstadoSubproceso.CANCELADO), m("CANCELADO", false, false, false, false));
        assertEquals(Optional.of(EstadoSubproceso.PUBLICADO), m("PUBLICADO", false, false, false, true));
        assertEquals(Optional.of(EstadoSubproceso.FINALIZADO), m("COMPLETADO", false, false, false, true));
        assertEquals(Optional.of(EstadoSubproceso.EN_EJECUCION), m("ASIGNADO", false, false, false, true));
        assertEquals(Optional.of(EstadoSubproceso.EN_EJECUCION), m("ASIGNADO", false, true, true, true));
        assertEquals(Optional.of(EstadoSubproceso.PENDIENTE), m("PENDIENTE", false, false, false, false));
        assertEquals(Optional.of(EstadoSubproceso.PENDIENTE), m("PENDIENTE", false, true, false, false));
    }

    @Test
    void pendienteDeUnaCitaEncargadaAlProgramadorEsProgramando() {
        assertEquals(Optional.of(EstadoSubproceso.PROGRAMANDO), m("PENDIENTE", false, true, true, false));
        // un análisis nunca pasa por el programador
        assertEquals(Optional.of(EstadoSubproceso.PENDIENTE), m("PENDIENTE", false, false, true, false));
    }

    @Test
    void pendienteDeUnServicioCanceladoSeCancela() {
        assertEquals(Optional.of(EstadoSubproceso.CANCELADO), m("PENDIENTE", true, false, false, false));
        assertEquals(Optional.of(EstadoSubproceso.CANCELADO), m("PENDIENTE", true, true, true, false));
    }

    @Test
    void unEstadoDesconocidoNoSeMapea() {
        assertEquals(Optional.empty(), m("RARO", false, false, false, false));
        assertEquals(Optional.empty(), m(null, false, false, false, false));
    }
}
