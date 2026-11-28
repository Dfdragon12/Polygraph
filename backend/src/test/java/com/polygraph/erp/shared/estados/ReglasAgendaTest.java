package com.polygraph.erp.shared.estados;

import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.estados.ReglasAgenda.Bloque;
import com.polygraph.erp.shared.estados.ReglasAgenda.Programado;
import com.polygraph.erp.shared.estados.ReglasAgenda.TipoBloque;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReglasAgendaTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 27);

    private static LocalDateTime a(int h, int m) { return DIA.atTime(h, m); }

    private static Programado cita(long id, int h, int m, int duracion) {
        return new Programado(id, a(h, m), duracion, "Poligrafía", "Eva Luada", 40, false);
    }

    @Test
    void laJornadaVaDeLas7ALas18() {
        assertEquals(java.time.LocalTime.of(7, 0), ReglasAgenda.JORNADA_INICIO);
        assertEquals(java.time.LocalTime.of(18, 0), ReglasAgenda.JORNADA_FIN);
    }

    @Test
    void unDiaSinCitasEsLibreSalvoElAlmuerzo() {
        List<Bloque> b = ReglasAgenda.armarDia(DIA, List.of(), 0);
        assertEquals(3, b.size());
        assertEquals(TipoBloque.LIBRE, b.get(0).tipo());
        assertEquals(a(7, 0), b.get(0).inicio());
        assertEquals(a(12, 0), b.get(0).fin());
        assertEquals(TipoBloque.BLOQUEADO, b.get(1).tipo());
        assertEquals(ReglasAgenda.MOTIVO_ALMUERZO, b.get(1).motivo());
        assertEquals(a(13, 0), b.get(1).fin());
        assertEquals(TipoBloque.LIBRE, b.get(2).tipo());
        assertEquals(a(18, 0), b.get(2).fin());
        assertEquals(11 * 60 - 60, ReglasAgenda.minutos(b, TipoBloque.LIBRE));
        assertEquals(60, ReglasAgenda.minutos(b, TipoBloque.BLOQUEADO));
        assertEquals(0, ReglasAgenda.minutos(b, TipoBloque.OCUPADO));
    }

    @Test
    void laDuracionDeCadaBloqueEsLaDelCatalogo() {
        // 15, 30, 85 y 110 minutos
        List<Bloque> b = ReglasAgenda.armarDia(DIA, List.of(cita(1, 7, 0, 15), cita(2, 8, 0, 30), cita(3, 9, 0, 85), cita(4, 14, 0, 110)), 0);
        var ocupados = b.stream().filter(x -> x.tipo() == TipoBloque.OCUPADO).toList();
        assertEquals(List.of(15L, 30L, 85L, 110L), ocupados.stream().map(Bloque::minutos).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L), ocupados.stream().map(Bloque::idSubproceso).toList());
        assertEquals("Eva Luada", ocupados.get(0).evaluado());
        assertEquals(40, ocupados.get(0).idServicio());
        assertEquals(15 + 30 + 85 + 110, ReglasAgenda.minutos(b, TipoBloque.OCUPADO));
    }

    @Test
    void losTramosSonSeguidosSinHuecosNiCruces() {
        List<Bloque> b = ReglasAgenda.armarDia(DIA, List.of(cita(1, 9, 0, 85), cita(2, 11, 30, 110), cita(3, 16, 0, 30)), 0);
        assertEquals(a(7, 0), b.get(0).inicio());
        assertEquals(a(18, 0), b.get(b.size() - 1).fin());
        for (int i = 1; i < b.size(); i++) assertEquals(b.get(i - 1).fin(), b.get(i).inicio(), "hueco o cruce en el tramo " + i);
        long suma = b.stream().mapToLong(Bloque::minutos).sum();
        assertEquals(11 * 60, suma);
    }

    @Test
    void unaCitaEnElAlmuerzoSigueSiendoUnaCita() {
        List<Bloque> b = ReglasAgenda.armarDia(DIA, List.of(cita(1, 11, 30, 90)), 0);      // 11:30 a 13:00
        assertEquals(90, ReglasAgenda.minutos(b, TipoBloque.OCUPADO));
        assertEquals(0, ReglasAgenda.minutos(b, TipoBloque.BLOQUEADO));                     // el almuerzo quedó cubierto por la cita
    }

    @Test
    void loQueSeSaleDeLaJornadaSeRecorta() {
        List<Bloque> b = ReglasAgenda.armarDia(DIA, List.of(cita(1, 17, 30, 120)), 0);     // 17:30 a 19:30
        assertEquals(30, ReglasAgenda.minutos(b, TipoBloque.OCUPADO));
        assertEquals(a(18, 0), b.get(b.size() - 1).fin());
    }

    @Test
    void elDesplazamientoSeBloqueaDespuesDeUnaVisitaPresencial() {
        Programado visita = new Programado(7L, a(8, 0), 60, "Visita", "Eva", 40, true);
        List<Bloque> sinDesplazamiento = ReglasAgenda.armarDia(DIA, List.of(visita), 0);
        assertEquals(0, sinDesplazamiento.stream().filter(x -> ReglasAgenda.MOTIVO_DESPLAZAMIENTO.equals(x.motivo())).count());
        List<Bloque> con = ReglasAgenda.armarDia(DIA, List.of(visita), 30);
        Bloque des = con.stream().filter(x -> ReglasAgenda.MOTIVO_DESPLAZAMIENTO.equals(x.motivo())).findFirst().orElseThrow();
        assertEquals(a(9, 0), des.inicio());
        assertEquals(a(9, 30), des.fin());
        assertEquals(30, des.minutos());
        // una cita que no es presencial no bloquea desplazamiento
        Programado virtual = new Programado(8L, a(8, 0), 60, "Poligrafía", "Eva", 40, false);
        assertEquals(0, ReglasAgenda.armarDia(DIA, List.of(virtual), 30).stream().filter(x -> ReglasAgenda.MOTIVO_DESPLAZAMIENTO.equals(x.motivo())).count());
    }

    @Test
    void cabeSoloSiElTramoLibreEsTanLargoComoLaTarea() {
        List<Bloque> b = ReglasAgenda.armarDia(DIA, List.of(cita(1, 7, 0, 100)), 0);      // libre de 8:40 a 12:00 = 200 min
        Bloque libre = b.stream().filter(x -> x.tipo() == TipoBloque.LIBRE).findFirst().orElseThrow();
        assertEquals(200, libre.minutos());
        assertTrue(ReglasAgenda.cabe(libre, 110));
        assertTrue(ReglasAgenda.cabe(libre, 200));
        assertFalse(ReglasAgenda.cabe(libre, 201));
        assertFalse(ReglasAgenda.cabe(b.get(0), 15));       // un tramo ocupado nunca cabe
    }

    @Test
    void atrasadoEnAgendaEsElDiaProgramadoYaPasadoSinHacerse() {
        LocalDate hoy = DIA;
        assertTrue(ReglasAgenda.atrasadoEnAgenda(EstadoSubproceso.EN_EJECUCION, DIA.minusDays(1).atTime(9, 0), hoy));
        assertTrue(ReglasAgenda.atrasadoEnAgenda(EstadoSubproceso.EN_EJECUCION, DIA.minusDays(30).atTime(9, 0), hoy));
        assertFalse(ReglasAgenda.atrasadoEnAgenda(EstadoSubproceso.EN_EJECUCION, a(7, 0), hoy));            // hoy no es atraso
        assertFalse(ReglasAgenda.atrasadoEnAgenda(EstadoSubproceso.EN_EJECUCION, DIA.plusDays(1).atTime(9, 0), hoy));
        assertFalse(ReglasAgenda.atrasadoEnAgenda(EstadoSubproceso.EN_EJECUCION, null, hoy));
        // lo que ya se hizo, se canceló o espera reprogramarse no es «atrasado en agenda»
        for (EstadoSubproceso e : new EstadoSubproceso[]{EstadoSubproceso.PENDIENTE, EstadoSubproceso.PROGRAMANDO, EstadoSubproceso.REPROGRAMADO,
                EstadoSubproceso.FINALIZADO, EstadoSubproceso.PUBLICADO, EstadoSubproceso.CANCELADO}) {
            assertFalse(ReglasAgenda.atrasadoEnAgenda(e, DIA.minusDays(3).atTime(9, 0), hoy), e.name());
        }
    }
}
