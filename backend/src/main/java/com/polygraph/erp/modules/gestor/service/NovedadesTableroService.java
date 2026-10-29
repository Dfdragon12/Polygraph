package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.gestor.dto.GraficasGestorResponse;
import com.polygraph.erp.modules.gestor.dto.GraficasGestorResponse.Abierta;
import com.polygraph.erp.modules.gestor.dto.GraficasGestorResponse.Categoria;
import com.polygraph.erp.modules.gestor.dto.GraficasGestorResponse.Conteo;
import com.polygraph.erp.modules.gestor.dto.GraficasGestorResponse.SemanaNovedades;
import com.polygraph.erp.modules.servicios.entity.NovedadSubproceso;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.NovedadSubprocesoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Novedades internas de subproceso para los tableros: cuántas siguen abiertas (y cuántas llevan
 *  más de 48 h), por categoría y tipo, registradas vs. resueltas por semana (8), tiempo promedio de
 *  respuesta (últimos 30 días) y las 5 abiertas más antiguas.
 *
 *  Alcance: el GESTOR ve las de los servicios de sus clientes; el PROGRAMADOR, las de los
 *  subprocesos que coordina; el analista/poligrafista/visitador, las que él mismo registró. Se usa
 *  un solo id y los otros van en -1 (ningún usuario lo tiene). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NovedadesTableroService {

    private final NovedadSubprocesoRepository repositorio;

    public GraficasGestorResponse.Novedades deGestor(Long idGestor) { return resumen(idGestor, -1L, -1L); }

    public GraficasGestorResponse.Novedades deProgramador(Long idProgramador) { return resumen(-1L, idProgramador, -1L); }

    /** Las que registró el analista/poligrafista/visitador (él solo ve las suyas, igual que en el formulario). */
    public GraficasGestorResponse.Novedades deEjecutor(Long idUsuario) { return resumen(-1L, -1L, idUsuario); }

    GraficasGestorResponse.Novedades resumen(Long idGestor, Long idProgramador, Long idRegistro) {
        LocalDateTime ahora = LocalDateTime.now();

        List<Categoria> porCategoria = repositorio.contarAbiertasPorCategoria(idGestor, idProgramador, idRegistro).stream()
                .map(f -> new Categoria(f[0] != null ? (String) f[0] : "Sin categoría", (String) f[1], (Long) f[2]))
                .toList();
        long abiertas = porCategoria.stream().mapToLong(Categoria::cantidad).sum();

        List<Conteo> porTipo = repositorio.contarAbiertasPorTipo(idGestor, idProgramador, idRegistro, PageRequest.of(0, 6)).stream()
                .map(f -> new Conteo((String) f[0], (Long) f[1]))
                .toList();

        // Registradas vs. resueltas, 8 semanas (lunes a domingo)
        LocalDate desde = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(7);
        Map<LocalDate, long[]> semanas = new LinkedHashMap<>();
        for (int i = 0; i < 8; i++) semanas.put(desde.plusWeeks(i), new long[2]);
        for (LocalDateTime f : repositorio.fechasRegistro(idGestor, idProgramador, idRegistro, desde.atStartOfDay())) {
            long[] s = semanas.get(f.toLocalDate().with(DayOfWeek.MONDAY));
            if (s != null) s[0]++;
        }
        List<Object[]> resueltas = repositorio.resueltasDesde(idGestor, idProgramador, idRegistro, desde.atStartOfDay());
        for (Object[] r : resueltas) {
            long[] s = semanas.get(((LocalDateTime) r[1]).toLocalDate().with(DayOfWeek.MONDAY));
            if (s != null) s[1]++;
        }
        List<SemanaNovedades> porSemana = semanas.entrySet().stream()
                .map(e -> new SemanaNovedades(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();

        // Tiempo promedio de respuesta, últimos 30 días (en horas con un decimal)
        LocalDateTime hace30 = ahora.minusDays(30);
        Double promedio = resueltas.stream()
                .filter(r -> r[0] != null && ((LocalDateTime) r[1]).isAfter(hace30))
                .mapToLong(r -> Duration.between((LocalDateTime) r[0], (LocalDateTime) r[1]).toMinutes())
                .average().stream().boxed().map(min -> Math.round(min / 6.0) / 10.0).findFirst().orElse(null);

        List<NovedadSubproceso> antiguas = repositorio.abiertasMasAntiguas(idGestor, idProgramador, idRegistro, PageRequest.of(0, 50));
        long mas48h = antiguas.stream().filter(n -> n.getFechaRegistro().isBefore(ahora.minusHours(48))).count();
        List<Abierta> masAntiguas = antiguas.stream().limit(5).map(NovedadesTableroService::aAbierta).toList();

        return new GraficasGestorResponse.Novedades(abiertas, mas48h, promedio, porCategoria, porTipo, porSemana, masAntiguas);
    }

    private static Abierta aAbierta(NovedadSubproceso n) {
        ServicioSubproceso sp = n.getServicioSubproceso();
        var candidato = sp.getServicio() != null ? sp.getServicio().getCandidato() : null;
        return new Abierta(
                n.getId(), sp.getServicio() != null ? sp.getServicio().getIdServicio() : null, sp.getId(),
                n.getTipo() != null ? n.getTipo().getNombre() : null,
                n.getTipo() != null && n.getTipo().getCategoria() != null ? n.getTipo().getCategoria().getColor() : null,
                sp.getTipoProgreso() != null ? sp.getTipoProgreso().getNombreProgreso() : null,
                candidato != null ? (candidato.getNombres() + " " + (candidato.getApellidos() != null ? candidato.getApellidos() : "")).trim() : null,
                n.getFechaRegistro());
    }
}
