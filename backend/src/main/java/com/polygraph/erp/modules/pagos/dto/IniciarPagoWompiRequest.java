package com.polygraph.erp.modules.pagos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * montoEnCentavos viaja como Long a propósito (nunca double/float, para evitar errores de
 * redondeo en dinero) y de todas formas NUNCA se usa para el cobro: el backend siempre
 * recalcula el monto real a partir de la orden de compra referenciada por facturaOConceptoId.
 */
public record IniciarPagoWompiRequest(
        @NotNull Integer clienteId,
        @NotNull Long facturaOConceptoId,
        @NotNull @Positive Long montoEnCentavos,
        @NotBlank String descripcion
) {
}
