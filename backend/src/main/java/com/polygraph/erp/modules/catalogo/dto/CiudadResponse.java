package com.polygraph.erp.modules.catalogo.dto;

import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.shared.enums.NivelCiudad;

public record CiudadResponse(
        Integer idCiudad,
        String nombreCiudad,
        String departamento,
        String codigoDaneCiudad,
        String codigoDaneDepto,
        NivelCiudad nivelCiudad,
        Boolean pendienteConfirmacion
) {
    public static CiudadResponse from(Ciudad c) {
        return new CiudadResponse(c.getIdCiudad(), c.getNombreCiudad(), c.getDepartamento(),
                c.getCodigoDaneCiudad(), c.getCodigoDaneDepto(), c.getNivelCiudad(), c.getPendienteConfirmacion());
    }
}
