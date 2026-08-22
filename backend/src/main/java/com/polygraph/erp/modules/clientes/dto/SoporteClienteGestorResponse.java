package com.polygraph.erp.modules.clientes.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Como SoporteClienteResponse pero con el cliente identificado — para el listado del gestor. */
public record SoporteClienteGestorResponse(
        Integer idSoporte,
        Integer idCliente,
        String nombreCliente,
        String tipoSoporte,
        String nombreTipo,
        String estado,
        LocalDate fechaEntrega,
        LocalDate fechaVencimiento,
        String nombreArchivo,
        String observaciones,
        String validadoPor,
        LocalDateTime fechaValidacion
) {}
