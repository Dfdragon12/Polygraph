package com.polygraph.erp.shared.estados;

import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * El reporte diario de productividad (SE-11). PURO: se calcula SOLO desde el registro de cambios (SE-03), no desde el estado actual de los subprocesos,
 * y el día se corta a las 11:59 p. m. hora de Colombia (las horas que recibe ya son hora de Colombia: cuenta todo lo anterior a la medianoche siguiente).
 *
 * <ul>
 *   <li>Asignados: subprocesos que entraron a EN_EJECUCION en el día (literal: también cuenta el que vuelve a EN_EJECUCION por una devolución o una reprogramación).</li>
 *   <li>Finalizados, Publicados: pasaron a ese estado en el día. Devueltos: llevan la marca DEVUELTO. Reprogramados: pasaron a REPROGRAMADO, por motivo.</li>
 *   <li>Abiertos al cierre: en EN_EJECUCION al corte. Por verificar al cierre: en FINALIZADO al corte (el gestor aún no los aprueba).</li>
 *   <li>Servicios por estado al cierre: la regla del filtro (SE-04) sobre el estado de cada subproceso al corte.</li>
 *   <li>Estudios completos publicados: quedaron completos en el día (no lo estaban al corte del día anterior).</li>
 *   <li>Vencidos al cierre: hoy se usa el estado de tiempo guardado; un día pasado se aproxima con la fecha de compromiso actual (ver {@link FilaGestor#vencidosAproximado}).</li>
 * </ul>
 * A cada persona se le atribuye lo que le pasó a los subprocesos que tenía asignados; al programador, lo de las citas que coordina.
 */
public final class ProductividadDiaria {

    private ProductividadDiaria() {}

    /** Un cambio del registro (ya en hora de Colombia), en el orden en que ocurrió. */
    public record Evento(Long idSubproceso, EstadoSubproceso anterior, EstadoSubproceso nuevo, String marca, String motivo, Long idResponsable,
                         LocalDateTime fecha) {}

    /** Un subproceso del universo del reporte. {@code creado}: día de la solicitud (desde ahí existe, en PENDIENTE, si el registro no dice otra cosa). */
    public record Subproceso(Long id, Integer idServicio, Long idGestor, Long idProgramador, LocalDate creado) {}

    /** {@code vencidaHoy}: el estado de tiempo guardado ahora es VENCIDA. */
    public record Servicio(Integer id, LocalDate compromiso, boolean vencidaHoy) {}

    public record FilaPersona(LocalDate fecha, Long idPersona, Rol rol, int asignados, int finalizados, int publicados, int devueltos,
                              Map<String, Integer> reprogramados, int abiertosAlCierre) {
        boolean vacia() {
            return asignados + finalizados + publicados + devueltos + abiertosAlCierre == 0 && (reprogramados == null || reprogramados.isEmpty());
        }
    }

    public record FilaGestor(LocalDate fecha, Long idGestor, int porVerificarAlCierre, int totalServicios,
                             Map<EstadoSubproceso, SemaforoEstados.Conteo> serviciosPorEstado, int estudiosCompletosPublicados,
                             int vencidosAlCierre, boolean vencidosAproximado) {}

    public record Resultado(List<FilaPersona> personas, List<FilaGestor> gestores) {}

    private static final Set<Rol> CON_REPROGRAMADOS = Set.of(Rol.PROGRAMADOR, Rol.POLIGRAFISTA, Rol.VISITADOR);

    public static Resultado calcular(LocalDate desde, LocalDate hasta, LocalDate hoy, List<Subproceso> subprocesos, List<Evento> eventos,
                                     Map<Integer, Servicio> servicios, Map<Long, Rol> roles) {
        Map<Long, List<Evento>> porSub = new HashMap<>();
        for (Evento e : eventos) porSub.computeIfAbsent(e.idSubproceso(), k -> new ArrayList<>()).add(e);
        porSub.values().forEach(l -> l.sort(Comparator.comparing(Evento::fecha)));      // estable: conserva el orden de inserción dentro del mismo instante

        List<FilaPersona> personas = new ArrayList<>();
        List<FilaGestor> gestores = new ArrayList<>();
        Map<Long, Subproceso> subPorId = new HashMap<>();
        subprocesos.forEach(s -> subPorId.put(s.id(), s));

        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            LocalDateTime ini = dia.atStartOfDay();
            LocalDateTime fin = dia.plusDays(1).atStartOfDay();        // corte: todo lo anterior a la medianoche = hasta las 11:59:59 p. m.

            // ── movimientos del día, por persona ──
            Map<Long, Acumulador> acum = new TreeMap<>();
            for (Evento e : eventos) {
                if (e.fecha().isBefore(ini) || !e.fecha().isBefore(fin)) continue;
                Subproceso s = subPorId.get(e.idSubproceso());
                if (s == null) continue;
                for (Long persona : personasDe(e.idResponsable(), s)) {
                    Acumulador a = acum.computeIfAbsent(persona, k -> new Acumulador());
                    if (e.nuevo() == EstadoSubproceso.EN_EJECUCION && e.anterior() != EstadoSubproceso.EN_EJECUCION) a.asignados.add(s.id());
                    if (e.nuevo() == EstadoSubproceso.FINALIZADO) a.finalizados.add(s.id());
                    if (e.nuevo() == EstadoSubproceso.PUBLICADO) a.publicados.add(s.id());
                    if ("DEVUELTO".equals(e.marca())) a.devueltos.add(s.id());
                    if (e.nuevo() == EstadoSubproceso.REPROGRAMADO) a.reprogramados.merge(e.motivo() == null ? "Sin motivo" : e.motivo(), 1, Integer::sum);
                }
            }
            // ── abiertos al cierre, por persona ──
            for (Subproceso s : subprocesos) {
                List<Evento> evs = porSub.getOrDefault(s.id(), List.of());
                if (estadoEn(s, evs, fin) != EstadoSubproceso.EN_EJECUCION) continue;
                for (Long persona : personasDe(responsableEn(evs, fin), s)) acum.computeIfAbsent(persona, k -> new Acumulador()).abiertos++;
            }
            for (var en : acum.entrySet()) {
                Rol rol = roles.get(en.getKey());
                Acumulador a = en.getValue();
                Map<String, Integer> repro = rol != null && CON_REPROGRAMADOS.contains(rol) ? new LinkedHashMap<>(a.reprogramados) : null;
                FilaPersona f = new FilaPersona(dia, en.getKey(), rol, a.asignados.size(), a.finalizados.size(), a.publicados.size(), a.devueltos.size(),
                        repro, a.abiertos);
                if (!f.vacia()) personas.add(f);
            }

            // ── gestor: estado de sus clientes al cierre ──
            Map<Long, List<Subproceso>> porGestor = new TreeMap<>();
            for (Subproceso s : subprocesos) if (s.idGestor() != null) porGestor.computeIfAbsent(s.idGestor(), k -> new ArrayList<>()).add(s);
            for (var g : porGestor.entrySet()) {
                List<SemaforoEstados.Fila> alCierre = filasEn(g.getValue(), porSub, fin);
                List<SemaforoEstados.Fila> alInicio = filasEn(g.getValue(), porSub, ini);
                if (alCierre.isEmpty()) continue;
                var conteo = SemaforoEstados.contar(alCierre);
                int porVerificar = (int) alCierre.stream().filter(f -> f.estado() == EstadoSubproceso.FINALIZADO).count();
                Set<Integer> completosAlCierre = completos(alCierre), completosAlInicio = completos(alInicio);
                int nuevos = (int) completosAlCierre.stream().filter(id -> !completosAlInicio.contains(id)).count();
                boolean esHoy = dia.equals(hoy);
                int vencidos = 0;
                for (Integer id : new TreeSet<>(alCierre.stream().map(SemaforoEstados.Fila::idServicio).toList())) {
                    if (completosAlCierre.contains(id)) continue;
                    Servicio sv = servicios.get(id);
                    if (sv == null) continue;
                    boolean vencida = esHoy ? sv.vencidaHoy() : sv.compromiso() != null && sv.compromiso().isBefore(dia);
                    if (vencida) vencidos++;
                }
                gestores.add(new FilaGestor(dia, g.getKey(), porVerificar, conteo.totalServicios(), conteo.porEstado(), nuevos, vencidos, !esHoy));
            }
        }
        return new Resultado(personas, gestores);
    }

    // ───────────────────────── piezas ─────────────────────────

    private static final class Acumulador {
        final Set<Long> asignados = new TreeSet<>(), finalizados = new TreeSet<>(), publicados = new TreeSet<>(), devueltos = new TreeSet<>();
        final Map<String, Integer> reprogramados = new TreeMap<>();
        int abiertos;
    }

    /** Quien carga con el movimiento: el responsable del subproceso y, aparte, el programador que lo coordina. */
    private static List<Long> personasDe(Long responsable, Subproceso s) {
        List<Long> r = new ArrayList<>(2);
        if (responsable != null) r.add(responsable);
        if (s.idProgramador() != null && !s.idProgramador().equals(responsable)) r.add(s.idProgramador());
        return r;
    }

    /** El estado del subproceso justo antes de {@code corte} (exclusivo); sin registro, PENDIENTE desde el día de la solicitud; antes de existir, null. */
    static EstadoSubproceso estadoEn(Subproceso s, List<Evento> eventosOrdenados, LocalDateTime corte) {
        EstadoSubproceso e = null;
        for (Evento ev : eventosOrdenados) {
            if (ev.fecha().isBefore(corte)) e = ev.nuevo(); else break;
        }
        if (e != null) return e;
        return s.creado() != null && s.creado().atStartOfDay().isBefore(corte) ? EstadoSubproceso.PENDIENTE : null;
    }

    private static Long responsableEn(List<Evento> eventosOrdenados, LocalDateTime corte) {
        Long r = null;
        for (Evento ev : eventosOrdenados) {
            if (!ev.fecha().isBefore(corte)) break;
            if (ev.idResponsable() != null) r = ev.idResponsable();
        }
        return r;
    }

    private static List<SemaforoEstados.Fila> filasEn(List<Subproceso> subs, Map<Long, List<Evento>> porSub, LocalDateTime corte) {
        List<SemaforoEstados.Fila> r = new ArrayList<>();
        for (Subproceso s : subs) {
            EstadoSubproceso e = estadoEn(s, porSub.getOrDefault(s.id(), List.of()), corte);
            if (e != null) r.add(new SemaforoEstados.Fila(s.idServicio(), e));
        }
        return r;
    }

    /** Servicios completos: tienen subprocesos vigentes y todos están publicados. */
    private static Set<Integer> completos(List<SemaforoEstados.Fila> filas) {
        Map<Integer, List<EstadoSubproceso>> porServicio = new HashMap<>();
        filas.forEach(f -> porServicio.computeIfAbsent(f.idServicio(), k -> new ArrayList<>()).add(f.estado()));
        Set<Integer> r = new TreeSet<>();
        porServicio.forEach((id, estados) -> {
            var avance = SemaforoEstados.avance(estados);
            if (avance.vigentes() > 0 && avance.publicados() == avance.vigentes()) r.add(id);
        });
        return r;
    }

    /** Para quien quiera una lista ordenada de los estados con su conteo (Excel y pantalla). */
    public static Map<EstadoSubproceso, SemaforoEstados.Conteo> enOrden(Map<EstadoSubproceso, SemaforoEstados.Conteo> m) {
        Map<EstadoSubproceso, SemaforoEstados.Conteo> r = new EnumMap<>(EstadoSubproceso.class);
        r.putAll(m);
        return r;
    }
}
