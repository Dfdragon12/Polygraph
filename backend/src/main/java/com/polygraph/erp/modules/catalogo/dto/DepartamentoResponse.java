package com.polygraph.erp.modules.catalogo.dto;

import com.polygraph.erp.modules.catalogo.entity.Departamento;

public record DepartamentoResponse(
        Integer idDepartamento,
        String nombre,
        String codigoDane,
        Boolean enUso
) {
    public static DepartamentoResponse from(Departamento d, boolean enUso) {
        return new DepartamentoResponse(d.getIdDepartamento(), d.getNombre(), d.getCodigoDane(), enUso);
    }
}
