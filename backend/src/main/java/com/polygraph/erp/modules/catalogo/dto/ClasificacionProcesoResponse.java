package com.polygraph.erp.modules.catalogo.dto;

public record ClasificacionProcesoResponse(
        Integer idClasificacion,
        String  codigo,
        String  nombre,
        String  descripcion,
        Boolean activo
) {}
