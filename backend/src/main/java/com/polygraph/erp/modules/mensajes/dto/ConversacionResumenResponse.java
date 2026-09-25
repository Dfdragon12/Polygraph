package com.polygraph.erp.modules.mensajes.dto;

import java.time.LocalDateTime;

public record ConversacionResumenResponse(
        Integer idCliente,
        String nombreCliente,
        // Un hilo por usuario del lado cliente — antes era una sola conversación por empresa.
        Long idUsuarioCliente,
        String nombreUsuarioCliente,
        String rolUsuarioCliente,
        String ultimoMensaje,
        LocalDateTime fechaUltimoMensaje,
        long noLeidos
) {}
