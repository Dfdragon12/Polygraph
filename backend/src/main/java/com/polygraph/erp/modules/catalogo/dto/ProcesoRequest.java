package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ProcesoRequest(
        @NotBlank @Size(max = 100) String nombreProceso,
        String descripcion,
        @NotNull Integer idClasificacion,
        BigDecimal valor,
        @Min(1) @Max(365) Integer diasHabilesEntrega,
        Boolean aplicaPrecioCiudad,
        // null = no cambia (nuevo proceso: sí la necesita)
        Boolean requiereHojaVida,
        // null = no cambia (NS-08)
        Boolean requiereDireccion,
        Boolean ciudadEsDePrueba,
        List<@Size(max = 80) String> puntosClave,
        // TP-01: null = no cambia
        @Size(max = 120) String tituloComercial,
        @Size(max = 160) String frasePrincipal,
        @Size(max = 400) String descripcionCorta,
        @Size(max = 6) List<@Size(max = 80) String> incluye,
        @Size(max = 200) String idealPara,
        @Size(max = 30) String unidadSingular,
        @Size(max = 30) String unidadPlural,
        @jakarta.validation.constraints.PositiveOrZero BigDecimal costoDirecto,
        Boolean visibleEnTienda,
        Boolean aplicaIva,
        // TP-11: null = no cambia
        Boolean ofrecerPospago,
        com.polygraph.erp.shared.enums.ModoPrecioPospago modoPrecioPospago,
        @jakarta.validation.constraints.PositiveOrZero BigDecimal precioPospagoPropio
) {}
