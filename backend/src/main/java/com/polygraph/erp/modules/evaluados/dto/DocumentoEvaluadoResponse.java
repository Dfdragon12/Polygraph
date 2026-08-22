package com.polygraph.erp.modules.evaluados.dto;

import com.polygraph.erp.modules.evaluados.entity.DocumentoEvaluado;

import java.time.LocalDateTime;

public record DocumentoEvaluadoResponse(
        Long id,
        String tipoDocumento,
        String nombreArchivo,
        LocalDateTime fechaCarga
) {
    public static DocumentoEvaluadoResponse from(DocumentoEvaluado d) {
        return new DocumentoEvaluadoResponse(d.getId(), d.getTipoDocumento(), d.getNombreArchivo(), d.getFechaCarga());
    }
}
