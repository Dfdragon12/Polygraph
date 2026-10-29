package com.polygraph.erp.modules.trabajos.dto;

import java.util.List;
import java.util.Map;

/** Foto del sistema de colas para el panel: cuánto hay en cada estado (BD) y cómo están los
 *  hilos y recursos AHORA (memoria del proceso). */
public record MetricasTrabajosResponse(
        /** tipo → estado → cantidad */
        Map<String, Map<String, Long>> conteos,
        long completadosUltimas24h,
        long fallidosUltimas24h,
        List<Pool> pools,
        Pool asincronoGeneral,
        Recurso libreOffice,
        Recurso conexionesBd,
        Memoria memoria
) {
    /** hilos = capacidad; enCurso = trabajando ahora; enCola = esperando en memoria (solo @Async). */
    public record Pool(String nombre, int hilos, int enCurso, int enCola, int capacidadCola, long rechazados) {}

    public record Recurso(int maximo, int enUso, int esperando) {}

    public record Memoria(long heapUsadoMb, long heapMaximoMb) {}
}
