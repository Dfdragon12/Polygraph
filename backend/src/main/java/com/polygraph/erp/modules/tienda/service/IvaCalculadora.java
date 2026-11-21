package com.polygraph.erp.modules.tienda.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** IVA por línea (TP-03): sobre la base de cada línea, redondeado a pesos (HALF_UP). El total es la suma de las líneas. Puro, sin Spring. */
public final class IvaCalculadora {

    private IvaCalculadora() {}

    /** IVA de una línea. 0 si el servicio no aplica IVA, el porcentaje es 0 o la base es 0. */
    public static BigDecimal iva(BigDecimal base, BigDecimal porcentaje, boolean aplica) {
        if (!aplica || base == null || porcentaje == null || porcentaje.signum() <= 0 || base.signum() <= 0) return BigDecimal.ZERO;
        return base.multiply(porcentaje).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }
}
