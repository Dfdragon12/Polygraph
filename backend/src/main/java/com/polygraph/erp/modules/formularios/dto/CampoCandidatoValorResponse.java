package com.polygraph.erp.modules.formularios.dto;

/** Un dato del candidato ya resuelto — clave (para identificarlo en código), etiqueta (para
 *  mostrar) y el valor real, siempre leído en vivo de la tabla de candidatos.
 *  PB-06: {@code valorSolicitud} solo viene cuando lo que escribió el evaluado en su hoja de vida
 *  (que es el {@code valor}) difiere de lo que trae la solicitud del cliente; si no, es null. */
public record CampoCandidatoValorResponse(String clave, String etiqueta, String valor, String valorSolicitud) {
    public CampoCandidatoValorResponse(String clave, String etiqueta, String valor) {
        this(clave, etiqueta, valor, null);
    }
}
