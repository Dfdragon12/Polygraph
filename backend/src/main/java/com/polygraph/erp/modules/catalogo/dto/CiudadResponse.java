package com.polygraph.erp.modules.catalogo.dto;

import com.polygraph.erp.modules.catalogo.entity.Ciudad;

public record CiudadResponse(
        Integer idCiudad,
        String nombreCiudad,
        String departamento,
        String codigoDaneCiudad,
        String codigoDaneDepto
) {
    public static CiudadResponse from(Ciudad c) {
        return new CiudadResponse(c.getIdCiudad(), c.getNombreCiudad(), c.getDepartamento(),
                c.getCodigoDaneCiudad(), c.getCodigoDaneDepto());
    }
}
