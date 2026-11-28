package com.polygraph.erp.modules.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Todo lo que el administrador ve en su tablero: la operación (servicios y trabajo del equipo), lo que
 *  necesita su atención, el riesgo, el dinero y la gente conectada. Se vuelve a pedir cada vez que
 *  cambia algo (avisos en tiempo real), así que todo se calcula con conteos livianos. */
public record DashboardAdminResponse(
        LocalDateTime generadoEn,
        // ── Usuarios (lo de siempre) ──
        long totalActivos,
        long internosActivos,
        long clientesActivos,
        List<UltimoLoginDto> ultimosLogins,
        List<ServicioCategoriaDto> serviciosPorCategoria,
        // ── Nuevo ──
        Servicios servicios,
        Trabajo trabajo,
        Atencion atencion,
        Riesgo riesgo,
        Finanzas finanzas,
        Equipo equipo
) {
    public record UltimoLoginDto(
            Long idUsuario,
            String nombre,
            String apellido,
            String email,
            String rol,
            Boolean activo,
            LocalDateTime ultimoAcceso
    ) {}

    public record ServicioCategoriaDto(
            String categoria,
            long total,
            long activos
    ) {}

    /** Solicitudes: el semáforo con números reales, lo nuevo y lo que corre contra el reloj. */
    public record Servicios(
            long total,
            /** En curso: pendiente + programando + en ejecución + reprogramado (finalizado falta publicarlo; publicado y cancelado están cerrados). */
            long enCurso,
            Map<String, Long> porEstado,
            long nuevasHoy,
            long nuevasSemana,
            long nuevasMes,
            /** Estado de tiempo VENCIDA: pasó su fecha de compromiso (días hábiles, con pausas). Del resumen único de tiempos. */
            long entregasVencidas,
            /** Estado de tiempo POR_VENCER: vence hoy o el siguiente día hábil. Del resumen único de tiempos. */
            long entregasPorVencer,
            /** Solicitudes creadas por día, los últimos 14 días (incluye días en cero). */
            List<DiaDto> porDia
    ) {}

    public record DiaDto(LocalDate fecha, long total) {}

    /** El trabajo de las personas: subprocesos por etapa y por rol, y quién tiene más a su cargo. */
    public record Trabajo(
            /** Subprocesos por etapa: PENDIENTE (sin asignar), ASIGNADO, COMPLETADO (por aprobar), PUBLICADO. */
            Map<String, Long> porEstado,
            List<RolDto> porRol,
            List<CargaDto> carga
    ) {}

    public record RolDto(String rol, long sinAsignar, long enCurso, long porAprobar) {}

    public record CargaDto(Long idUsuario, String nombre, String rol, long enCurso) {}

    /** Lo que hoy pide una decisión o una revisión del administrador. */
    public record Atencion(
            long reversionesPendientes,
            long documentosPorRevisar,
            long novedadesAbiertas,
            /** Novedades abiertas hace más de 48 horas. */
            long novedadesViejas,
            long ciudadesPorConfirmar,
            long trabajosFallidos,
            long trabajosEnCola,
            long facturasVencidas,
            long pagosPendientesViejos,
            long clientesSinGestor
    ) {}

    /** Calificación de riesgo de los estudios calculados en los últimos 30 días (nivel que vale). */
    public record Riesgo(long bajo, long medio, long alto, long total) {}

    public record Finanzas(
            long pagosAprobadosHoy,
            /** En centavos (así viaja el dinero de Wompi). */
            long montoAprobadoHoyCentavos,
            long pagosAprobadosMes,
            long montoAprobadoMesCentavos,
            long facturasPendientes,
            BigDecimal montoFacturasPendientes,
            long facturasVencidas,
            BigDecimal montoFacturasVencidas
    ) {}

    /** Gente: equipo por rol y quién está conectado AHORA (con la plataforma abierta). */
    public record Equipo(
            Map<String, Long> internosPorRol,
            long conectadosInternos,
            long conectadosClientes,
            long conectadosCandidatos,
            long clientesNuevosMes
    ) {}
}
