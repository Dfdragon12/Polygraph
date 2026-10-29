package com.polygraph.erp.shared.estados;

import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * El semáforo de estado (SE-04): UN solo cálculo para todos los perfiles. Pura: recibe filas (servicio, estado de un subproceso) y
 * devuelve conteos. El semáforo es un FILTRO, no una partición:
 * <ul>
 *   <li>un servicio pertenece al estado E si al menos uno de sus subprocesos está en E (puede estar en varios a la vez);</li>
 *   <li>cada estado cuenta servicios distintos, y aparte cuántos subprocesos hay en él;</li>
 *   <li>la suma de los contadores puede superar el total: el total es la cantidad de servicios.</li>
 * </ul>
 */
public final class SemaforoEstados {

    private SemaforoEstados() {}

    /** Un subproceso de un servicio, con su estado. */
    public record Fila(Integer idServicio, EstadoSubproceso estado) {}

    public record Conteo(int servicios, int subprocesos) {}

    /** {@code porEstado} trae SIEMPRE los siete estados, en el orden de pantalla. */
    public record Resultado(int totalServicios, Map<EstadoSubproceso, Conteo> porEstado) {}

    public static Resultado contar(Collection<Fila> filas) {
        Map<EstadoSubproceso, Set<Integer>> servicios = new EnumMap<>(EstadoSubproceso.class);
        Map<EstadoSubproceso, Integer> subprocesos = new EnumMap<>(EstadoSubproceso.class);
        Set<Integer> todos = new LinkedHashSet<>();
        for (Fila f : filas) {
            servicios.computeIfAbsent(f.estado(), e -> new LinkedHashSet<>()).add(f.idServicio());
            subprocesos.merge(f.estado(), 1, Integer::sum);
            todos.add(f.idServicio());
        }
        Map<EstadoSubproceso, Conteo> porEstado = new EnumMap<>(EstadoSubproceso.class);
        for (EstadoSubproceso e : EstadoSubproceso.enOrden()) {
            porEstado.put(e, new Conteo(servicios.getOrDefault(e, Set.of()).size(), subprocesos.getOrDefault(e, 0)));
        }
        return new Resultado(todos.size(), porEstado);
    }

    /** Los servicios que pertenecen al estado: los que tienen al menos un subproceso en él. */
    public static Set<Integer> serviciosEn(EstadoSubproceso estado, Collection<Fila> filas) {
        Set<Integer> r = new TreeSet<>();
        for (Fila f : filas) if (f.estado() == estado) r.add(f.idServicio());
        return r;
    }

    /** Avance de un servicio: subprocesos publicados sobre los no cancelados. */
    public record Avance(int publicados, int vigentes) {
        /** 0.0 a 1.0; un servicio sin subprocesos vigentes no tiene avance. */
        public double fraccion() { return vigentes == 0 ? 0 : (double) publicados / vigentes; }
    }

    public static Avance avance(Collection<EstadoSubproceso> estadosDelServicio) {
        int vigentes = 0, publicados = 0;
        for (EstadoSubproceso e : estadosDelServicio) {
            if (e == EstadoSubproceso.CANCELADO) continue;
            vigentes++;
            if (e == EstadoSubproceso.PUBLICADO) publicados++;
        }
        return new Avance(publicados, vigentes);
    }

    public enum EtiquetaPublicacion {
        NINGUNA(null),
        PUBLICADO_PARCIAL("Publicado parcial"),
        ESTUDIO_COMPLETO_PUBLICADO("Estudio completo publicado");

        private final String texto;

        EtiquetaPublicacion(String texto) { this.texto = texto; }

        public String texto() { return texto; }
    }

    /** «Publicado parcial» si hay publicados pero no todos los no cancelados; «Estudio completo publicado» si lo están todos. */
    public static EtiquetaPublicacion etiqueta(Avance a) {
        if (a.publicados() == 0 || a.vigentes() == 0) return EtiquetaPublicacion.NINGUNA;
        return a.publicados() == a.vigentes() ? EtiquetaPublicacion.ESTUDIO_COMPLETO_PUBLICADO : EtiquetaPublicacion.PUBLICADO_PARCIAL;
    }

    /** Los estados que ve cada perfil. Cliente, gestor y administrador ven los siete. */
    public static List<EstadoSubproceso> estadosVisibles(Rol rol) {
        return switch (rol) {
            case PROGRAMADOR -> List.of(EstadoSubproceso.PROGRAMANDO, EstadoSubproceso.EN_EJECUCION, EstadoSubproceso.REPROGRAMADO, EstadoSubproceso.FINALIZADO);
            case ANALISTA_INTERNO -> List.of(EstadoSubproceso.EN_EJECUCION, EstadoSubproceso.FINALIZADO, EstadoSubproceso.PUBLICADO);
            case POLIGRAFISTA, VISITADOR -> List.of(EstadoSubproceso.EN_EJECUCION, EstadoSubproceso.REPROGRAMADO, EstadoSubproceso.FINALIZADO, EstadoSubproceso.PUBLICADO);
            default -> EstadoSubproceso.enOrden();
        };
    }

    /** Analista, poligrafista, visitador y programador cuentan SUS subprocesos; los demás perfiles, servicios. */
    public static boolean cuentaSubprocesos(Rol rol) {
        return rol == Rol.PROGRAMADOR || rol == Rol.ANALISTA_INTERNO || rol == Rol.POLIGRAFISTA || rol == Rol.VISITADOR;
    }
}
