package com.polygraph.erp.modules.catalogo.dto;

import com.polygraph.erp.shared.enums.EstadoSubproceso;

import java.util.List;

public record EstadoSubprocesoResponse(String codigo, String nombre, String color, int orden, String textoCliente) {

    public static EstadoSubprocesoResponse de(EstadoSubproceso e) {
        return new EstadoSubprocesoResponse(e.name(), e.nombre(), e.color(), e.orden(), e.textoCliente());
    }

    /** Los siete, en el orden de pantalla. */
    public static List<EstadoSubprocesoResponse> catalogo() {
        return EstadoSubproceso.enOrden().stream().map(EstadoSubprocesoResponse::de).toList();
    }
}
