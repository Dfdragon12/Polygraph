package com.polygraph.erp.modules.pagos.dto;

public record IniciarPagoResponse(
        String urlCheckout,
        Boolean modoSimulado
) {}
