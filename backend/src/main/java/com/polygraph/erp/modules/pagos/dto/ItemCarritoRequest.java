package com.polygraph.erp.modules.pagos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ItemCarritoRequest(
        @NotNull(message = "Debe indicar el proceso")
        Integer idProceso,

        // Tienda actual (interruptor tiendaPorPlanes apagado): cantidad libre.
        @Min(value = 1, message = "La cantidad debe ser al menos 1")
        Integer cantidad,

        // Tienda por planes (TP-02): plan (1, 5, 20, 100 o 200 unidades), cobertura y cuántos paquetes de ese plan.
        @Min(value = 1, message = "Las unidades del plan deben ser al menos 1")
        Integer unidadesPlan,
        String cobertura,
        @Min(value = 1, message = "Debe comprar al menos 1 paquete")
        @jakarta.validation.constraints.Max(value = 1000, message = "Máximo 1000 paquetes por ítem")
        Integer cantidadPaquetes,

        // Precio por unidad que mostró el navegador; solo para detectar diferencias (el backend siempre manda).
        java.math.BigDecimal precioUnitarioEsperado
) {}
