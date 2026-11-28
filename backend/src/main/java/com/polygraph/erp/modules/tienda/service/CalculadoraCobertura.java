package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.shared.enums.NivelCiudad;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

/**
 * Reglas puras de la cobertura por sede o nacional (TP-08). Sin Spring ni base de datos.
 * <ul>
 *   <li>Ciudad sede: sin recargo. Ciudad PRINCIPAL que no es sede (Cali, Barranquilla) e INTERMEDIA: recargo de ciudad intermedia.
 *       MUNICIPIO_SECUNDARIO (y ciudad sin clasificar): recargo de municipio.</li>
 *   <li>Paquete nacional: solo para servicios con recargo; precio por unidad = precio del plan por sede + factor % del recargo intermedio,
 *       redondeado a $1.000. Una unidad nacional nunca genera recargo, ni siquiera en un municipio.</li>
 * </ul>
 */
public final class CalculadoraCobertura {

    public static final String LOCAL = "LOCAL";
    public static final String NACIONAL = "NACIONAL";
    private static final BigDecimal MIL = BigDecimal.valueOf(1000);

    private CalculadoraCobertura() {}

    /** De qué fila de precios sale el recargo de una ciudad; vacío si es sede (no paga recargo). */
    public static Optional<NivelCiudad> nivelDeRecargo(boolean esSede, NivelCiudad nivel) {
        if (esSede) return Optional.empty();
        if (nivel == NivelCiudad.PRINCIPAL || nivel == NivelCiudad.INTERMEDIA) return Optional.of(NivelCiudad.INTERMEDIA);
        return Optional.of(NivelCiudad.MUNICIPIO_SECUNDARIO);   // sin clasificar = nunca se asume el más barato
    }

    /** Recargo por unidad consumida (sin IVA). La cobertura nacional nunca paga recargo. */
    public static BigDecimal recargo(boolean aplicaPrecioCiudad, boolean esSede, NivelCiudad nivel, String cobertura, Map<NivelCiudad, BigDecimal> recargos) {
        if (!aplicaPrecioCiudad || NACIONAL.equals(cobertura)) return BigDecimal.ZERO;
        return nivelDeRecargo(esSede, nivel).map(n -> recargos.getOrDefault(n, BigDecimal.ZERO)).orElse(BigDecimal.ZERO);
    }

    /** ¿Se ofrece el paquete nacional? Solo servicios que tienen recargo de ciudad intermedia. */
    public static boolean admiteNacional(boolean aplicaPrecioCiudad, BigDecimal recargoIntermedio) {
        return aplicaPrecioCiudad && recargoIntermedio != null && recargoIntermedio.signum() > 0;
    }

    /** Precio por unidad del paquete nacional: precio por sede + factor % × recargo intermedio, a miles de pesos (mitad hacia arriba). */
    public static BigDecimal precioNacional(BigDecimal precioPorSede, BigDecimal recargoIntermedio, BigDecimal factorPorcentaje) {
        BigDecimal extra = recargoIntermedio.multiply(factorPorcentaje).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        return precioPorSede.add(extra).divide(MIL, 0, RoundingMode.HALF_UP).multiply(MIL);
    }

    /** Qué bolsa se usa primero: nacional si la ciudad tiene recargo, por sede si es sede. */
    public static String bolsaPreferida(boolean ciudadConRecargo) {
        return ciudadConRecargo ? NACIONAL : LOCAL;
    }
}
