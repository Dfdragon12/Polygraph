package com.polygraph.erp.modules.catalogo.dto;

/** totalTipos: cuántos tipos tiene (activos o no) — si es 0 la categoría se puede eliminar. */
public record CategoriaNovedadResponse(
        Integer idCategoriaNovedad,
        String clave,
        String nombre,
        String color,
        Integer orden,
        Boolean activo,
        long totalTipos) {}
