package com.polygraph.erp.modules.tienda.dto;

import java.math.BigDecimal;
import java.util.List;

/** Un plan de un servicio tal como lo ve el cliente. Precios SIN IVA. Nunca trae el costo directo. */
public record PlanPublicoResponse(
        Integer unidades,
        String nombre,
        boolean destacado,
        Integer vigenciaMeses,
        BigDecimal precioUnitario,
        BigDecimal precioPaquete,
        BigDecimal ahorro,
        /** Descuento por unidad frente al precio del plan de 1 (p. ej. 12.5). */
        BigDecimal descuentoPorcentaje,
        List<String> beneficios,
        /** TP-08: precio por unidad del paquete nacional (precio por sede + factor % del recargo intermedio, a miles). Null si no se ofrece. */
        BigDecimal precioUnitarioNacional,
        BigDecimal precioPaqueteNacional
) {
    public PlanPublicoResponse(Integer unidades, String nombre, boolean destacado, Integer vigenciaMeses, BigDecimal precioUnitario,
                               BigDecimal precioPaquete, BigDecimal ahorro, BigDecimal descuentoPorcentaje, List<String> beneficios) {
        this(unidades, nombre, destacado, vigenciaMeses, precioUnitario, precioPaquete, ahorro, descuentoPorcentaje, beneficios, null, null);
    }

    /** Copia con el precio del paquete nacional. */
    public PlanPublicoResponse conNacional(BigDecimal unitario) {
        return new PlanPublicoResponse(unidades, nombre, destacado, vigenciaMeses, precioUnitario, precioPaquete, ahorro, descuentoPorcentaje, beneficios,
                unitario, unitario.multiply(BigDecimal.valueOf(unidades)));
    }
}
