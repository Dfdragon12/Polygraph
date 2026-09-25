package com.polygraph.erp.modules.clientes.dto;

import java.math.BigDecimal;

/** Una fila de la tabla "Gestión de cuentas" — estado de cuenta de un cliente, prepago o pospago,
 *  para verlos todos juntos en un solo lugar. Los campos de un tipo vienen null para el otro (ver
 *  ClienteAdminService.listarEstadoCuentas). */
public record EstadoCuentaClienteResponse(
        Integer idCliente,
        String nit,
        String nombreCliente,
        String tipoCliente,              // "PREPAGO" | "POSPAGO"
        Integer totalUnidadesDisponibles, // solo prepago — suma de saldo disponible de todos los procesos
        BigDecimal limiteCredito,        // solo pospago
        BigDecimal creditoDisponible,    // solo pospago
        String estadoMora,               // solo pospago
        Boolean requiereAprobacion       // solo pospago
) {}
