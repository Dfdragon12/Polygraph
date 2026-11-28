package com.polygraph.erp.modules.catalogo.dto;

import com.polygraph.erp.modules.catalogo.entity.DocumentoRequeridoProceso;

public record DocumentoRequeridoResponse(
        Integer id,
        String clave,
        String nombre,
        Boolean obligatorio,
        Integer orden
) {
    public static DocumentoRequeridoResponse from(DocumentoRequeridoProceso d) {
        return new DocumentoRequeridoResponse(d.getId(), d.getClave(), d.getNombre(), d.getObligatorio(), d.getOrden());
    }
}
