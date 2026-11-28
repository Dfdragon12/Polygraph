package com.polygraph.erp.modules.evaluados.dto;

import com.polygraph.erp.modules.evaluados.entity.DocumentoEvaluado;

import java.time.LocalDateTime;

/** Documento visto por el CANDIDATO. estadoRevision + observacionRevision le dicen si se lo
 *  aprobaron o qué tiene que corregir; nunca quién lo revisó (usuario interno). */
public record DocumentoEvaluadoResponse(
        Long id,
        String tipoDocumento,
        String nombreArchivo,
        LocalDateTime fechaCarga,
        String estadoRevision,
        String observacionRevision,
        LocalDateTime fechaRevision
) {
    public static DocumentoEvaluadoResponse from(DocumentoEvaluado d) {
        return new DocumentoEvaluadoResponse(d.getId(), d.getTipoDocumento(), d.getNombreArchivo(), d.getFechaCarga(),
                d.getEstadoRevision() != null ? d.getEstadoRevision().name() : null,
                d.getObservacionRevision(), d.getFechaRevision());
    }
}
