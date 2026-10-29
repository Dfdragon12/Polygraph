package com.polygraph.erp.modules.catalogo.dto;

import com.polygraph.erp.modules.catalogo.entity.PreguntaPoligrafia;

public record PreguntaPoligrafiaResponse(
        Integer id,
        String nombre,
        Boolean requiereDetalle,
        Integer orden,
        Boolean activo
) {
    public static PreguntaPoligrafiaResponse de(PreguntaPoligrafia p) {
        return new PreguntaPoligrafiaResponse(p.getId(), p.getNombre(), p.getRequiereDetalle(), p.getOrden(), p.getActivo());
    }
}
