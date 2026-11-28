package com.polygraph.erp.modules.pagos.dto;

/** Nunca incluye privateKey, integritySecret ni eventsSecret — solo datos públicos del Web Checkout. */
public record IniciarPagoWompiResponse(
        String publicKey,
        String referencia,
        Long montoEnCentavos,
        String moneda,
        String firmaIntegridad,
        String redirectUrl,
        /** URL completa del Web Checkout de Wompi (redirect de página completa) — a esto se navega
         * directamente, nunca se arma la URL en el frontend. */
        String checkoutUrl
) {
}
