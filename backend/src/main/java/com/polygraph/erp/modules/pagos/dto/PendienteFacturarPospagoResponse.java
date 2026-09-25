package com.polygraph.erp.modules.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un cliente pospago con consumo sin facturar todavía — panel "Pospago: servicios pendientes de
 *  facturar" de Facturacion.jsx, cruzando todos los clientes pospago a la vez (ver
 *  ClientePospagoService.listarPendientesGlobal). */
public record PendienteFacturarPospagoResponse(
        Integer idCliente,
        String nit,
        String nombreCliente,
        int cantidadServicios,
        BigDecimal montoTotal,
        LocalDateTime primerMovimientoPendiente
) {}
