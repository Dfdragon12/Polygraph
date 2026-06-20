package com.polygraph.erp.modules.catalogo.dto;

public record PasoProcesoResponse(
        Integer id,
        Integer idTipoProgreso,
        String nombreProgreso,
        String descripcion,
        Integer ordenEnProceso,
        Boolean habilitado,
        Boolean obligatorio,
        Boolean tipoActivo
) {}
