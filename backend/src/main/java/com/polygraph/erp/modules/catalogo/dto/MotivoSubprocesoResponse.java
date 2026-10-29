package com.polygraph.erp.modules.catalogo.dto;

import com.polygraph.erp.modules.catalogo.entity.MotivoSubproceso;

public record MotivoSubprocesoResponse(Integer id, String tipo, String nombre, Boolean requiereTexto, Integer orden, Boolean activo, String codigo) {
    public static MotivoSubprocesoResponse de(MotivoSubproceso m) {
        return new MotivoSubprocesoResponse(m.getId(), m.getTipo().name(), m.getNombre(), m.getRequiereTexto(), m.getOrden(), m.getActivo(), m.getCodigo());
    }
}
