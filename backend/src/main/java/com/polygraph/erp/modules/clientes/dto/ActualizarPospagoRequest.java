package com.polygraph.erp.modules.clientes.dto;

import com.polygraph.erp.shared.enums.EstadoMora;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/** Actualización parcial — cualquier campo en null no se toca. Solo aplica a clientes POSPAGO. */
public record ActualizarPospagoRequest(
        @DecimalMin(value = "0", message = "El límite de crédito no puede ser negativo")
        BigDecimal limiteCredito,

        @DecimalMin(value = "0", message = "El crédito disponible no puede ser negativo")
        BigDecimal creditoDisponible,

        EstadoMora estadoMora,

        Boolean requiereAprobacion
) {}
