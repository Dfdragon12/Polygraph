package com.polygraph.erp.modules.clientes.dto;

import com.polygraph.erp.shared.enums.EstadoMora;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * Actualización parcial — cualquier campo en null no se toca. Solo aplica a clientes POSPAGO.
 * creditoDisponible NO se edita aquí — es 100% calculado por el sistema (se descuenta al
 * consumir servicios y se restituye al pagarse una factura), solo se ajusta automáticamente
 * por el delta cuando cambia limiteCredito (ver ClienteAdminService.actualizarPospago).
 */
public record ActualizarPospagoRequest(
        @DecimalMin(value = "0", message = "El límite de crédito no puede ser negativo")
        BigDecimal limiteCredito,

        EstadoMora estadoMora,

        Boolean requiereAprobacion
) {}
