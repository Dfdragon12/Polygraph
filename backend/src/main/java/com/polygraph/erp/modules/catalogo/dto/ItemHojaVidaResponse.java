package com.polygraph.erp.modules.catalogo.dto;

import com.polygraph.erp.modules.catalogo.entity.ItemHojaVida;
import com.polygraph.erp.modules.catalogo.entity.ListaHojaVida;

public record ItemHojaVidaResponse(
        Integer id,
        ListaHojaVida lista,
        String nombre,
        String descripcion,
        Integer orden,
        Boolean activo,
        Boolean enlaceCorto
) {
    public static ItemHojaVidaResponse de(ItemHojaVida i) {
        return new ItemHojaVidaResponse(i.getId(), i.getLista(), i.getNombre(), i.getDescripcion(),
                i.getOrden(), i.getActivo(), Boolean.TRUE.equals(i.getEnlaceCorto()));
    }
}
