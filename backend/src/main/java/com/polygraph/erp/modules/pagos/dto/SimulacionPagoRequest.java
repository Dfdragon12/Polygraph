package com.polygraph.erp.modules.pagos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SimulacionPagoRequest(
        @NotBlank(message = "Debe indicar el resultado")
        @Pattern(regexp = "APROBADO|RECHAZADO", message = "El resultado debe ser APROBADO o RECHAZADO")
        String resultado
) {}
