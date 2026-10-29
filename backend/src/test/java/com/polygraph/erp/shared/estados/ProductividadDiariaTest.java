package com.polygraph.erp.shared.estados;

import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.estados.ProductividadDiaria.Evento;
import com.polygraph.erp.shared.estados.ProductividadDiaria.FilaGestor;
import com.polygraph.erp.shared.estados.ProductividadDiaria.FilaPersona;
import com.polygraph.erp.shared.estados.ProductividadDiaria.Servicio;
import com.polygraph.erp.shared.estados.ProductividadDiaria.Subproceso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.polygraph.erp.shared.enums.EstadoSubproceso.*;
import static org.assertj.core.api.Assertions.assertThat;

class ProductividadDiariaTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 27);
    private static final long ANALISTA = 20, POLIGRAFISTA = 21, PROGRAMADOR = 5, GESTOR = 9;
    private static final Map<Long, Rol> ROLES = Map.of(ANALISTA, Rol.ANALISTA_INTERNO, POLIGRAFISTA, Rol.POLIGRAFISTA, PROGRAMADOR, Rol.PROGRAMADOR);

    private static Evento ev(long sub, EstadoSubproceso de, EstadoSubproceso a, String marca, String motivo, long resp, LocalDateTime t) {
        return new Evento(sub, de, a, marca, motivo, resp, t);
    }

    private static FilaPersona persona(ProductividadDiaria.Resultado r, LocalDate dia, long id) {
        return r.personas().stream().filter(f -> f.fecha().equals(dia) && f.idPersona() == id).findFirst().orElse(null);
    }

    private static ProductividadDiaria.Resultado calcular(LocalDate desde, LocalDate hasta, LocalDate hoy, List<Subproceso> subs, List<Evento> evs,
                                                          Map<Integer, Servicio> servicios) {
        return ProductividadDiaria.calcular(desde, hasta, hoy, subs, evs, servicios, ROLES);
    }

    @Test
    @DisplayName("Asignados, finalizados y publicados: lo que pasó en el día, y la medianoche corta el día (23:59:59 es de hoy, 00:00:00 es de mañana)")
    void movimientosDelDiaYCorte() {
        var subs = List.of(new Subproceso(1L, 1, GESTOR, null, D.minusDays(5)), new Subproceso(2L, 2, GESTOR, null, D.minusDays(5)));
        var evs = List.of(
                ev(1, PENDIENTE, EN_EJECUCION, null, null, ANALISTA, D.atTime(9, 0)),
                ev(1, EN_EJECUCION, FINALIZADO, null, null, ANALISTA, D.atTime(23, 59, 59)),          // último segundo del día
                ev(1, FINALIZADO, PUBLICADO, null, null, ANALISTA, D.plusDays(1).atStartOfDay()),       // ya es del día siguiente
                ev(2, PENDIENTE, EN_EJECUCION, null, null, ANALISTA, D.atTime(10, 0)));
        var r = calcular(D, D.plusDays(1), D.plusDays(1), subs, evs, Map.of());
        FilaPersona hoy = persona(r, D, ANALISTA);
        assertThat(hoy.asignados()).isEqualTo(2);
        assertThat(hoy.finalizados()).isEqualTo(1);
        assertThat(hoy.publicados()).isZero();
        assertThat(persona(r, D.plusDays(1), ANALISTA).publicados()).isEqualTo(1);
    }

    @Test
    @DisplayName("Devueltos por la marca DEVUELTO; el subproceso vuelve a EN_EJECUCION y cuenta abierto")
    void devueltos() {
        var subs = List.of(new Subproceso(1L, 1, GESTOR, null, D.minusDays(5)));
        var evs = List.of(
                ev(1, PENDIENTE, EN_EJECUCION, null, null, ANALISTA, D.minusDays(1).atTime(9, 0)),
                ev(1, EN_EJECUCION, FINALIZADO, null, null, ANALISTA, D.minusDays(1).atTime(15, 0)),
                ev(1, FINALIZADO, EN_EJECUCION, "DEVUELTO", "Falta soporte", ANALISTA, D.atTime(11, 0)));
        FilaPersona f = persona(calcular(D, D, D, subs, evs, Map.of()), D, ANALISTA);
        assertThat(f.devueltos()).isEqualTo(1);
        assertThat(f.abiertosAlCierre()).isEqualTo(1);
    }

    @Test
    @DisplayName("Reprogramados por motivo solo en programador, poligrafista y visitador; el analista no trae esa columna")
    void reprogramadosPorMotivo() {
        var subs = List.of(new Subproceso(1L, 1, GESTOR, PROGRAMADOR, D.minusDays(5)), new Subproceso(2L, 2, GESTOR, null, D.minusDays(5)));
        var evs = List.of(
                ev(1, EN_EJECUCION, REPROGRAMADO, null, "No se presentó", POLIGRAFISTA, D.atTime(10, 0)),
                ev(1, REPROGRAMADO, EN_EJECUCION, "REPROGRAMADA", null, POLIGRAFISTA, D.atTime(12, 0)),
                ev(1, EN_EJECUCION, REPROGRAMADO, null, "Aviso: no asistirá", POLIGRAFISTA, D.atTime(15, 0)),
                ev(2, EN_EJECUCION, REPROGRAMADO, null, "No se presentó", ANALISTA, D.atTime(10, 0)));
        var r = calcular(D, D, D, subs, evs, Map.of());
        assertThat(persona(r, D, POLIGRAFISTA).reprogramados()).containsEntry("No se presentó", 1).containsEntry("Aviso: no asistirá", 1);
        assertThat(persona(r, D, PROGRAMADOR).reprogramados()).containsEntry("No se presentó", 1);     // el programador responde por las citas que coordina
        assertThat(persona(r, D, ANALISTA)).isNull();                                  // el analista no tiene columna de reprogramados: sin otro movimiento no hay fila
    }

    @Test
    @DisplayName("Abiertos al cierre sale del registro AL CORTE, no del estado actual: lo finalizado después no cuenta en el día anterior")
    void abiertosAlCorte() {
        var subs = List.of(new Subproceso(1L, 1, GESTOR, null, D.minusDays(5)));
        var evs = List.of(
                ev(1, PENDIENTE, EN_EJECUCION, null, null, ANALISTA, D.atTime(9, 0)),
                ev(1, EN_EJECUCION, FINALIZADO, null, null, ANALISTA, D.plusDays(1).atTime(9, 0)));
        var r = calcular(D, D.plusDays(1), D.plusDays(1), subs, evs, Map.of());
        assertThat(persona(r, D, ANALISTA).abiertosAlCierre()).isEqualTo(1);
        assertThat(persona(r, D.plusDays(1), ANALISTA).abiertosAlCierre()).isZero();
    }

    @Test
    @DisplayName("Gestor: por verificar, servicios por estado (regla del filtro), estudio completo publicado una sola vez y vencidos")
    void filaDelGestor() {
        // servicio 1: dos subprocesos, uno publicado hoy y otro ya publicado → queda completo hoy
        // servicio 2: un subproceso finalizado sin verificar, vencido
        // servicio 3: un subproceso que nunca tuvo registro → PENDIENTE desde su solicitud
        var subs = List.of(
                new Subproceso(1L, 1, GESTOR, null, D.minusDays(9)), new Subproceso(2L, 1, GESTOR, null, D.minusDays(9)),
                new Subproceso(3L, 2, GESTOR, null, D.minusDays(9)), new Subproceso(4L, 3, GESTOR, null, D.minusDays(2)));
        var evs = List.of(
                ev(1, FINALIZADO, PUBLICADO, null, null, ANALISTA, D.minusDays(2).atTime(9, 0)),
                ev(2, FINALIZADO, PUBLICADO, null, null, ANALISTA, D.atTime(14, 0)),
                ev(3, EN_EJECUCION, FINALIZADO, null, null, ANALISTA, D.minusDays(1).atTime(9, 0)));
        var servicios = Map.of(1, new Servicio(1, D.plusDays(3), false), 2, new Servicio(2, D.minusDays(2), true), 3, new Servicio(3, D.plusDays(5), false));
        var r = calcular(D, D.plusDays(1), D, subs, evs, servicios);
        FilaGestor f = r.gestores().stream().filter(g -> g.fecha().equals(D)).findFirst().orElseThrow();
        assertThat(f.porVerificarAlCierre()).isEqualTo(1);
        assertThat(f.totalServicios()).isEqualTo(3);
        assertThat(f.serviciosPorEstado().get(PUBLICADO).servicios()).isEqualTo(1);
        assertThat(f.serviciosPorEstado().get(FINALIZADO).servicios()).isEqualTo(1);
        assertThat(f.serviciosPorEstado().get(PENDIENTE).servicios()).isEqualTo(1);
        assertThat(f.estudiosCompletosPublicados()).isEqualTo(1);
        assertThat(f.vencidosAlCierre()).isEqualTo(1);
        assertThat(f.vencidosAproximado()).isFalse();                       // hoy: se usa el estado de tiempo guardado
        FilaGestor manana = r.gestores().stream().filter(g -> g.fecha().equals(D.plusDays(1))).findFirst().orElseThrow();
        assertThat(manana.estudiosCompletosPublicados()).isZero();           // ya estaba completo: no se cuenta otra vez
        assertThat(manana.vencidosAproximado()).isTrue();
    }

    @Test
    @DisplayName("Un subproceso no existe antes del día de su solicitud")
    void noExisteAntesDeSuSolicitud() {
        var subs = List.of(new Subproceso(1L, 1, GESTOR, null, D));
        var r = calcular(D.minusDays(1), D, D, subs, List.of(), Map.of(1, new Servicio(1, null, false)));
        assertThat(r.gestores()).hasSize(1);
        assertThat(r.gestores().get(0).fecha()).isEqualTo(D);
    }
}
