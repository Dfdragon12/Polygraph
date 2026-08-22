package com.polygraph.erp.modules.clientes.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ValidarSoporteRequest(
        @NotNull Boolean aprobado,
        @Size(max = 500) String observaciones
) {}
