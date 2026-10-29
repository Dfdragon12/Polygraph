package com.polygraph.erp.modules.dashboard.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** El panorama de TODO el sistema para el administrador, en un periodo que él escoge (7, 30 o 90 días):
 *  qué se pidió, quién lo pide, cómo van los candidatos, los clientes, el dinero y qué hay configurado. */
public record PanoramaAdminResponse(
        int dias,
        LocalDate desde,
        LocalDate hasta,
        Solicitudes solicitudes,
        List<Conteo> topProcesos,
        List<Conteo> topClientes,
        Clientes clientes,
        Candidatos candidatos,
        Dinero dinero,
        Sistema sistema
) {
    /** Etiqueta + cantidad (para rankings y barras). */
    public record Conteo(String nombre, long total) {}

    public record Dia(LocalDate fecha, long creadas, long cerradas, long pagadoCentavos) {}

    public record Solicitudes(
            long creadas,
            /** Las que llegaron al estado PUBLICADO dentro del periodo. */
            long publicadas,
            long canceladas,
            /** Publicadas sobre creadas, en %, para ver si el equipo está entregando. */
            int porcentajePublicadas,
            /** Promedio de días entre solicitar y publicar, de las publicadas del periodo. */
            double diasPromedioEntrega,
            /** Solicitudes del periodo por estado actual. */
            Map<String, Long> porEstado,
            List<Dia> porDia
    ) {}

    public record Clientes(
            long total,
            long activos,
            long prepago,
            long pospago,
            long juridicas,
            long naturales,
            long nuevosPeriodo,
            long sinGestor,
            /** Clientes con al menos una solicitud en el periodo. */
            long conActividad,
            long mensajesSinLeer
    ) {}

    public record Candidatos(
            long linksGenerados,
            long linksUsados,
            long linksBloqueados,
            long linksRevocados,
            /** Links vigentes que el candidato todavía no ha usado. */
            long linksPorUsar,
            long hojasDeVidaEnviadas,
            long documentosPorRevisar,
            long documentosDevueltos
    ) {}

    public record Dinero(
            long pagosAprobados,
            long montoAprobadoCentavos,
            long pagosRechazados,
            long pagosPendientes,
            long facturasEmitidas,
            long facturasPagadas
    ) {}

    /** Lo que hay configurado y en marcha en la plataforma. */
    public record Sistema(
            long procesosActivos,
            long procesosTotal,
            long ciudades,
            long hallazgosActivos,
            long tiposNovedadActivos,
            long enlacesExternos,
            long internosActivos,
            long internosInactivos,
            long trabajosCompletadosPeriodo,
            long trabajosFallidos
    ) {}

    /** Una fila del detalle de un tema (lo que se abre al hacer clic en un recuadro). */
    public record Item(String titulo, String detalle, LocalDateTime fecha, String ruta) {}

    public record Detalle(String tema, String titulo, long total, List<Item> items, String rutaVerTodo) {}
}
