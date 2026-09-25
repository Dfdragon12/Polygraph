package com.polygraph.erp.modules.catalogo.dto;

import java.util.List;

/** enUso: cuántas novedades ya lo usan — si es > 0 no se puede eliminar, solo desactivar. */
public record TipoNovedadResponse(
        Integer idTipoNovedad,
        String clave,
        String nombre,
        Integer idCategoriaNovedad,
        String categoriaNombre,
        String categoriaColor,
        List<String> roles,
        String textoAyuda,
        Integer orden,
        Boolean activo,
        long enUso) {}
