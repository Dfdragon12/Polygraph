package com.polygraph.erp.modules.servicios.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * El reporte diario de productividad (SE-11). Calculado desde el registro de cambios con corte a las 11:59 p. m. hora de Colombia.
 * {@code personas}: una fila por persona y día (asignados, finalizados, publicados, devueltos, reprogramados por motivo, abiertos al cierre);
 * {@code gestores}: una fila por gestor y día (por verificar, servicios por estado al cierre, estudios completos publicados, vencidos).
 */
public record ProductividadResponse(LocalDate desde, LocalDate hasta, String corte, String zona, List<FilaPersona> personas, List<FilaGestor> gestores,
                                    Opciones opciones) {

    /** Lo que se puede elegir en los filtros dentro del alcance de quien consulta (sin aplicar cliente ni tipo). */
    public record Opcion(Long id, String nombre) {}

    public record Opciones(List<Opcion> clientes, List<Opcion> tipos, List<Opcion> personas) {}

    public record FilaPersona(LocalDate fecha, Long idPersona, String nombre, String rol, int asignados, int finalizados, int publicados, int devueltos,
                              Map<String, Integer> reprogramados, int abiertosAlCierre) {}

    /** {@code serviciosPorEstado}: servicios distintos por estado, en el orden de pantalla (un servicio cuenta en cada estado donde tenga un subproceso). */
    public record FilaGestor(LocalDate fecha, Long idGestor, String nombre, int porVerificarAlCierre, int totalServicios, List<EstadoConteo> serviciosPorEstado,
                             int estudiosCompletosPublicados, int vencidosAlCierre, boolean vencidosAproximado) {}

    public record EstadoConteo(String codigo, String nombre, int servicios, int subprocesos) {}
}
