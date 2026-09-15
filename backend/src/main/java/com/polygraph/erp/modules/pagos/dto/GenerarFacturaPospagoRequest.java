package com.polygraph.erp.modules.pagos.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record GenerarFacturaPospagoRequest(
        @NotNull LocalDate fechaVencimiento
) {}
