package com.polygraph.erp.modules.tienda.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs de administración de la tienda (TP-04). Solo los ve el administrador. */
public final class PreciosPlanDtos {

    private PreciosPlanDtos() {}

    /** precioUnitario null = ese plan no se vende para el servicio. */
    public record EntradaPrecio(Integer unidades, BigDecimal precioUnitario) {}

    public record SimulacionRequest(List<EntradaPrecio> precios, BigDecimal costoDirecto) {}

    public record GuardarPreciosRequest(List<EntradaPrecio> precios) {}

    /** Una fila por plan visible, con lo que el administrador necesita para decidir. Todo lo calcula el backend. */
    public record FilaPlan(
            Integer unidades,
            String nombre,
            boolean destacado,
            Integer vigenciaMeses,
            BigDecimal precioUnitario,
            BigDecimal precioPaquete,
            BigDecimal ahorro,
            BigDecimal descuentoVsAnterior,
            BigDecimal descuentoTotal,
            BigDecimal margenPorcentaje,
            List<String> alertas
    ) {}

    public record Analisis(
            List<FilaPlan> planes,
            BigDecimal margenMinimoPorcentaje,
            BigDecimal descuentoMaximoPorcentaje,
            boolean valido,
            String errorEscalera
    ) {}

    public record EntradaBeneficio(String texto, Integer desdeUnidades) {}

    public record BeneficioResponse(String texto, Integer desdeUnidades, Integer orden) {}

    public record GuardarBeneficiosRequest(List<EntradaBeneficio> beneficios) {}

    public record EntradaPlan(Integer unidades, String nombre, Boolean visible, Boolean destacado, Integer vigenciaMeses) {}

    public record GuardarPlanesRequest(List<EntradaPlan> planes) {}

    public record PlanAdminResponse(Integer unidades, String nombre, boolean visible, boolean destacado, Integer vigenciaMeses) {}

    public record ParametroResponse(String clave, String valor, String tipo, String descripcion, String actualizadoPor, LocalDateTime fechaActualizacion) {}

    public record CambiarParametroRequest(String valor) {}

    public record HistorialPrecioResponse(LocalDateTime fecha, String usuario, String campo, String valorAnterior, String valorNuevo) {}
}
