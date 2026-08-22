package com.polygraph.erp.modules.pagos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CrearOrdenRequest(
        @NotEmpty @Valid List<ItemCarritoRequest> items,
        String codigoCupon
) {}
