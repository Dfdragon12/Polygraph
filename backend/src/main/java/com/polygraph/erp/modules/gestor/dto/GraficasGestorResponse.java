package com.polygraph.erp.modules.gestor.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Datos agregados para las gráficas del tablero del gestor (solo servicios de SUS clientes).
 *
 *  solicitudesPorSemana: últimas 12 semanas (lunes de cada semana → solicitudes recibidas).
 *  entregas: servicios en curso agrupados por qué tan cerca está su entrega.
 *  porCliente / porProceso: servicios en curso, los 6 con más carga.
 *  novedades: novedades internas de subproceso (las reporta quien ejecuta, las resuelve el gestor). */
public record GraficasGestorResponse(
        List<Semana> solicitudesPorSemana,
        Entregas entregas,
        List<Conteo> porCliente,
        List<Conteo> porProceso,
        Novedades novedades
) {
    public record Semana(LocalDate inicio, long cantidad) {}

    public record Entregas(long vencidas, long hoy, long manana, long estaSemana, long despues, long sinFecha) {}

    public record Conteo(String nombre, long cantidad) {}

    /** abiertas: sin resolver hoy · abiertasMas48h: llevan más de 48 h sin respuesta ·
     *  horasPromedioResolucion: de las resueltas en los últimos 30 días (null si no hay) ·
     *  porSemana: registradas y resueltas en las últimas 8 semanas ·
     *  masAntiguas: las 5 abiertas que más llevan esperando. */
    public record Novedades(long abiertas, long abiertasMas48h, Double horasPromedioResolucion,
                            List<Categoria> porCategoria, List<Conteo> porTipo,
                            List<SemanaNovedades> porSemana, List<Abierta> masAntiguas) {}

    public record Categoria(String nombre, String color, long cantidad) {}

    public record SemanaNovedades(LocalDate inicio, long registradas, long resueltas) {}

    public record Abierta(Long id, Integer idServicio, Long idServicioSubproceso, String tipo, String categoriaColor,
                          String subproceso, String candidato, LocalDateTime fechaRegistro) {}
}
