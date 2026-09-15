package com.polygraph.erp.modules.pagos.dto;

import java.util.List;

public record FacturaPospagoDetalleResponse(
        FacturaPospagoResponse factura,
        List<MovimientoCreditoPospagoResponse> movimientos,
        List<IntentoPagoResponse> intentosPago
) {}
