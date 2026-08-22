package com.polygraph.erp.modules.pagos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ItemCarritoRequest(
        @NotNull(message = "Debe indicar el proceso")
        Integer idProceso,

        @NotNull(message = "Debe indicar la cantidad")
        @Min(value = 1, message = "La cantidad debe ser al menos 1")
        Integer cantidad
) {}
