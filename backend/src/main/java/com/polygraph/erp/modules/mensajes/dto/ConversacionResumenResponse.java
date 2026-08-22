package com.polygraph.erp.modules.mensajes.dto;

import java.time.LocalDateTime;

public record ConversacionResumenResponse(
        Integer idCliente,
        String nombreCliente,
        String ultimoMensaje,
        LocalDateTime fechaUltimoMensaje,
        long noLeidos
) {}
