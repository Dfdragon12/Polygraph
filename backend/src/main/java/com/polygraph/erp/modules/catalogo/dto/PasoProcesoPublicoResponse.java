package com.polygraph.erp.modules.catalogo.dto;

public record PasoProcesoPublicoResponse(
        String nombreProgreso,
        String descripcion,
        Integer ordenEnProceso,
        Boolean obligatorio
) {}
