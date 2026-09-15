package com.polygraph.erp.modules.pagos.dto;

import java.math.BigDecimal;

public record EstadoCuentaPospagoResponse(
        Integer idCliente,
        BigDecimal limiteCredito,
        BigDecimal creditoDisponible,
        BigDecimal consumidoSinFacturar,
        BigDecimal montoFacturasPendientes,
        String estadoMora
) {}
