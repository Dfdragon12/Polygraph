package com.polygraph.erp.modules.pagos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CrearOrdenRequest(
        @NotEmpty(message = "El carrito no puede estar vacío")
        @Valid
        List<ItemCarritoRequest> items
) {}
