package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos;
import com.polygraph.erp.modules.tienda.entity.PlanTienda;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Regla pura de precios por plan (TP-02), sin Spring ni base de datos.
 * El plan de 1 unidad vale el precio base del servicio; los demás, el valor unitario del tramo con esa cantidad mínima.
 * Un plan sin precio no se muestra. Cada plan con precio debe costar por unidad ESTRICTAMENTE menos que el plan con precio anterior.
 */
public final class CalculadoraPlanes {

    private CalculadoraPlanes() {}

    /** Plan con su precio por unidad ya resuelto (pesos enteros). */
    public record PlanConPrecio(PlanTienda plan, BigDecimal precioUnitario) {}

    /** Planes visibles (por unidades ascendente) que sí tienen precio para este servicio. */
    public static List<PlanConPrecio> conPrecio(List<PlanTienda> planesVisibles, BigDecimal valorBase, Map<Integer, BigDecimal> tramosPorCantidad) {
        List<PlanConPrecio> r = new ArrayList<>();
        for (PlanTienda plan : planesVisibles) {
            BigDecimal precio = plan.getUnidades() == 1 ? valorBase : tramosPorCantidad.get(plan.getUnidades());
            if (precio == null) continue;
            r.add(new PlanConPrecio(plan, entero(precio)));
        }
        return r;
    }

    /** Vacío si la escalera de precios es válida; si no, el mensaje que se le muestra al administrador. */
    public static Optional<String> validarEscalera(List<PlanTienda> planesVisibles, BigDecimal valorBase, Map<Integer, BigDecimal> tramosPorCantidad) {
        PlanConPrecio anterior = null;
        for (PlanConPrecio actual : conPrecio(planesVisibles, valorBase, tramosPorCantidad)) {
            if (anterior != null && actual.precioUnitario().compareTo(anterior.precioUnitario()) >= 0) {
                return Optional.of("El " + actual.plan().getNombre() + " (" + actual.plan().getUnidades() + " unidades) debe costar por unidad menos que el "
                        + anterior.plan().getNombre() + " (" + pesos(anterior.precioUnitario()) + "). Precio recibido: " + pesos(actual.precioUnitario()) + ".");
            }
            anterior = actual;
        }
        return Optional.empty();
    }

    /**
     * Una fila por plan visible para la pantalla de precios del administrador (TP-04): precio, paquete, descuento frente al plan anterior
     * con precio y frente al plan de 1, margen bruto si hay costo directo y alertas. Un plan sin precio sale con precios null.
     */
    public static List<PreciosPlanDtos.FilaPlan> analizar(List<PlanTienda> planesVisibles, BigDecimal valorBase, Map<Integer, BigDecimal> tramos,
                                                         BigDecimal costoDirecto, BigDecimal margenMinimo) {
        List<PreciosPlanDtos.FilaPlan> filas = new ArrayList<>();
        BigDecimal base = valorBase != null && valorBase.signum() > 0 ? entero(valorBase) : null;
        BigDecimal anterior = null;
        for (PlanTienda plan : planesVisibles) {
            BigDecimal bruto = plan.getUnidades() == 1 ? valorBase : tramos.get(plan.getUnidades());
            BigDecimal precio = bruto != null ? entero(bruto) : null;
            List<String> alertas = new ArrayList<>();
            BigDecimal paquete = null, ahorro = null, descAnt = null, descTotal = null, margen = null;
            if (precio != null) {
                paquete = precio.multiply(BigDecimal.valueOf(plan.getUnidades()));
                if (base != null) ahorro = base.multiply(BigDecimal.valueOf(plan.getUnidades())).subtract(paquete).max(BigDecimal.ZERO);
                if (anterior != null) {
                    descAnt = descuentoPorcentaje(precio, anterior);
                    if (precio.compareTo(anterior) >= 0) alertas.add("No baja de precio frente al plan anterior (" + pesos(anterior) + " por unidad).");
                }
                if (base != null) descTotal = descuentoPorcentaje(precio, base);
                if (costoDirecto != null && precio.signum() > 0) {
                    margen = precio.subtract(costoDirecto).multiply(BigDecimal.valueOf(100)).divide(precio, 1, RoundingMode.HALF_UP);
                    if (margenMinimo != null && margen.compareTo(margenMinimo) < 0) {
                        alertas.add("Margen bajo: " + margen.stripTrailingZeros().toPlainString() + " % (mínimo " + margenMinimo.stripTrailingZeros().toPlainString() + " %).");
                    }
                }
                anterior = precio;
            }
            filas.add(new PreciosPlanDtos.FilaPlan(plan.getUnidades(), plan.getNombre(), Boolean.TRUE.equals(plan.getDestacado()),
                    plan.getVigenciaMeses(), precio, paquete, ahorro, descAnt, descTotal, margen, alertas));
        }
        return filas;
    }

    /** Descuento por unidad frente al plan de 1, en % con un decimal sin ceros sobrantes (35.000 sobre 40.000 = 12.5). */
    public static BigDecimal descuentoPorcentaje(BigDecimal precioUnitario, BigDecimal valorBase) {
        if (valorBase == null || valorBase.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal d = BigDecimal.ONE.subtract(precioUnitario.divide(entero(valorBase), 6, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        return d.signum() <= 0 ? BigDecimal.ZERO : (d.scale() < 0 ? d.setScale(0) : d);
    }

    public static BigDecimal entero(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP);
    }

    private static String pesos(BigDecimal v) {
        return "$" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO")).format(v);
    }
}
