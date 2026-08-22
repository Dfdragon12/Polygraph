package com.polygraph.erp.modules.pagos.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Evento de webhook de Wompi ya parseado. "data" es el árbol JSON crudo bajo la clave
 * "data" del evento — las rutas listadas en signature.properties (p. ej. "transaction.id")
 * se resuelven sobre este árbol, no sobre el evento completo.
 */
public record WompiEventDTO(
        String event,
        JsonNode data,
        WompiEventSignatureDTO signature,
        String timestamp
) {
}
