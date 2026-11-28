package com.polygraph.erp.modules.catalogo.dto;

import com.polygraph.erp.modules.catalogo.entity.Pais;

public record PaisResponse(Integer idPais, String nombre, String codigoIso) {
    public static PaisResponse from(Pais p) {
        return new PaisResponse(p.getIdPais(), p.getNombre(), p.getCodigoIso());
    }
}
