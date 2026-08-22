package com.polygraph.erp.modules.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrdenCompraResponse(
        Long idOrdenCompra,
        String referencia,
        BigDecimal montoTotal,
        String estado,
        Boolean modoSimulado,
        String urlCheckout,
        List<ItemOrdenResponse> items,
        LocalDateTime fechaCreacion
) {}
