package com.polygraph.erp.modules.mensajes.dto;

import java.time.LocalDateTime;

public record MensajeResponse(
        Long id,
        String origen,
        String mensaje,
        LocalDateTime fechaEnvio,
        LocalDateTime fechaExpiracion,
        Boolean leido,
        String nombreEmisor,
        // Quién lo escribió, específicamente — un cliente puede tener varios usuarios (admin +
        // analistas) en el mismo hilo compartido; el frontend lo usa para saber si un mensaje del
        // lado CLIENTE es del usuario que tiene la sesión abierta o de otro de su equipo.
        Long idEmisor
) {}
