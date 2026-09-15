package com.polygraph.erp.modules.formularios.dto;

/** Un dato del candidato ya resuelto — clave (para identificarlo en código), etiqueta (para
 *  mostrar) y el valor real, siempre leído en vivo de la tabla de candidatos. */
public record CampoCandidatoValorResponse(String clave, String etiqueta, String valor) {}
