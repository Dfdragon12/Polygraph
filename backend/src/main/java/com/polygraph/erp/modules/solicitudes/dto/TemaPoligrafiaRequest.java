package com.polygraph.erp.modules.solicitudes.dto;

/** Un tema que el cliente pide evaluar en la poligrafía. `detalle` solo se usa en los temas que lo
 *  piden (Otros ¿Cuál?). */
public record TemaPoligrafiaRequest(Integer idPregunta, String detalle) {}
