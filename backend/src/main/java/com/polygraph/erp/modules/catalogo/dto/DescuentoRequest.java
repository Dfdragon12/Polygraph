package com.polygraph.erp.modules.catalogo.dto;

import com.polygraph.erp.shared.enums.AlcanceDescuento;
import com.polygraph.erp.shared.enums.TipoDescuento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DescuentoRequest(
        @NotBlank String nombre,
        String codigo,
        @NotNull TipoDescuento tipo,
        @NotNull @Positive BigDecimal valor,
        @NotNull AlcanceDescuento alcance,
        Integer idClasificacion,
        Integer idProceso,
        BigDecimal montoMaximoDescuento,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        Boolean activo
) {
}
