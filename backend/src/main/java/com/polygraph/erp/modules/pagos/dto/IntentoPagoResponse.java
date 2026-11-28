package com.polygraph.erp.modules.pagos.dto;

import java.time.LocalDateTime;

/** Un intento de pago (PagoWompi) hecho para una factura — para poder cruzar la referencia con el panel de Wompi. */
public record IntentoPagoResponse(
        String referencia,
        String estado,
        String metodoPago,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion
) {}
