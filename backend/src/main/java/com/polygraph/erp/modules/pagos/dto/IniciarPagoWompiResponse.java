package com.polygraph.erp.modules.pagos.dto;

/** Nunca incluye privateKey, integritySecret ni eventsSecret — solo datos públicos del Web Checkout. */
public record IniciarPagoWompiResponse(
        String publicKey,
        String referencia,
        Long montoEnCentavos,
        String moneda,
        String firmaIntegridad,
        String redirectUrl
) {
}
