package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.NotNull;

public record PasoProcesoRequest(
        @NotNull Integer idTipoProgreso,
        @NotNull Integer ordenEnProceso,
        Boolean habilitado,
        Boolean obligatorio
) {}
