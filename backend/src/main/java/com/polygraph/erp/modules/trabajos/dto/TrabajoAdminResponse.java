package com.polygraph.erp.modules.trabajos.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** Fila del panel Admin → Trabajos en segundo plano. Aquí sí va el error técnico. */
public record TrabajoAdminResponse(
        UUID id,
        String tipo,
        String estado,
        String usuario,
        int progreso,
        int intentos,
        int maxIntentos,
        String mensajeUsuario,
        String errorTecnico,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        LocalDateTime disponibleDesde,
        Long duracionMs,
        boolean reintentable
) {}
