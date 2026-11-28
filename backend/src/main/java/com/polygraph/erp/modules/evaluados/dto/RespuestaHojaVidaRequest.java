package com.polygraph.erp.modules.evaluados.dto;

/** Respuesta del candidato a una pregunta de declaración: Sí/No y, si es Sí, la explicación. */
public record RespuestaHojaVidaRequest(
        Integer idPregunta,
        Boolean respuesta,
        String detalle
) {}
