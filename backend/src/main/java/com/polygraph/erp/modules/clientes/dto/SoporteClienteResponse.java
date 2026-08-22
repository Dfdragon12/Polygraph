package com.polygraph.erp.modules.clientes.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Un "slot" de documento legal del cliente — existe incluso antes de que se cargue el archivo. */
public record SoporteClienteResponse(
        Integer idSoporte,
        String tipoSoporte,
        String nombreTipo,
        Integer vigenciaDias,
        String estado,
        LocalDate fechaEntrega,
        LocalDate fechaVencimiento,
        String nombreArchivo,
        String observaciones,
        String validadoPor,
        LocalDateTime fechaValidacion
) {}
