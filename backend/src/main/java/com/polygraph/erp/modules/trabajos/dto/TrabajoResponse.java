package com.polygraph.erp.modules.trabajos.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

/** Lo que ve quien pidió el trabajo mientras hace polling. Respuesta pequeña a propósito (se pide
 *  muchas veces): sin error técnico, sin parámetros internos.
 *
 *  posicionEnCola: cuántos del mismo tipo hay antes (solo en EN_COLA; null en otro estado).
 *  resultado: el dato pequeño del trabajo (ej. resumen de la carga masiva), solo en COMPLETADO.
 *  tieneArchivo: si hay algo que descargar en GET /api/v1/trabajos/{id}/resultado. */
public record TrabajoResponse(
        UUID id,
        String tipo,
        String estado,
        int progreso,
        Long posicionEnCola,
        String mensaje,
        JsonNode resultado,
        boolean tieneArchivo,
        String nombreArchivo,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaFin
) {}
