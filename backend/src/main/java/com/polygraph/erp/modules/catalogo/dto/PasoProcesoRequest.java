package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.constraints.NotNull;

public record PasoProcesoRequest(
        @NotNull Integer idTipoProgreso,
        // El orden ya no se define aquí — siempre lo determina tipos_progreso.orden (la cadena del catálogo).
        Integer ordenEnProceso,
        Boolean habilitado,
        Boolean obligatorio
) {}
