package com.polygraph.erp.modules.catalogo.dto;

import com.polygraph.erp.shared.enums.NivelCiudad;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Monto adicional (COP) para un nivel de ciudad — se suma al precio final (valor base o tramo
 * de volumen ya aplicado), no lo reemplaza. Puede ser 0 (ej. nivel PRINCIPAL sin recargo). */
public record PrecioCiudadRequest(
        @NotNull(message = "El nivel es obligatorio")
        NivelCiudad nivelCiudad,

        @NotNull(message = "El valor es obligatorio")
        @DecimalMin(value = "0", inclusive = true, message = "El valor no puede ser negativo")
        BigDecimal valor
) {}
