package com.polygraph.erp.modules.solicitudes.dto;

import java.math.BigDecimal;

/**
 * TP-08: el aviso del paso 2 de Nueva solicitud, apenas el cliente elige la ciudad. Calculado en el servidor.
 * recargo e iva son por la unidad que se usaría; cobertura = bolsa de la que saldría (LOCAL por sede / NACIONAL, que nunca paga recargo).
 */
public record RecargoCiudadResponse(
        boolean aplica,
        String cobertura,
        BigDecimal recargo,
        BigDecimal iva,
        BigDecimal total,
        String ciudad,
        String mensaje
) {
    public static RecargoCiudadResponse sinRecargo(String cobertura, String mensaje) {
        return new RecargoCiudadResponse(false, cobertura, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, mensaje);
    }
}
