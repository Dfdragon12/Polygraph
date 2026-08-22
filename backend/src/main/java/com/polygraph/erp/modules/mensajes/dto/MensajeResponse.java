package com.polygraph.erp.modules.mensajes.dto;

import java.time.LocalDateTime;

public record MensajeResponse(
        Long id,
        String origen,
        String mensaje,
        LocalDateTime fechaEnvio,
        LocalDateTime fechaExpiracion,
        Boolean leido,
        String nombreEmisor
) {}
