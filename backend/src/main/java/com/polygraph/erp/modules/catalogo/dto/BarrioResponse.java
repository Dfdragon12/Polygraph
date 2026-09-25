package com.polygraph.erp.modules.catalogo.dto;

import com.polygraph.erp.modules.catalogo.entity.Barrio;

public record BarrioResponse(
        Integer idBarrio,
        String nombre,
        Integer idCiudad,
        String nombreCiudad,
        Boolean pendienteConfirmacion
) {
    public static BarrioResponse from(Barrio b) {
        return new BarrioResponse(b.getIdBarrio(), b.getNombre(), b.getCiudad().getIdCiudad(),
                b.getCiudad().getNombreCiudad(), b.getPendienteConfirmacion());
    }
}
