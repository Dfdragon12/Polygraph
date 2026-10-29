package com.polygraph.erp.modules.evaluados.dto;

import com.polygraph.erp.modules.catalogo.dto.DocumentoRequeridoResponse;

import java.util.List;

public record LinkValidacionResponse(
        Integer idServicio,
        String cedulaEvaluado,
        String nombresEvaluado,
        String apellidosEvaluado,
        String cargo,
        String proceso,
        // Empresa cliente que pidió el estudio (nombre comercial, o razón social / nombre si no tiene).
        String empresa,
        boolean hojaVidaCompletada,
        // ¿Ya aceptó la autorización de tratamiento de datos (Ley 1581)? Si no, el portal solo
        // muestra la pantalla de autorización (POST /link/{token}/autorizacion).
        boolean autorizacionDatos,
        // Cuándo la aceptó: el candidato la puede consultar pero no retirarla desde el portal.
        java.time.LocalDateTime fechaAutorizacion,
        // Tamaño máximo de lo que carga el candidato (app.candidato.documentos.*), para avisarle
        // antes de subir. Solo PDF, JPG o PNG (ValidadorArchivoCandidato).
        long maxPdfMb,
        long maxImagenMb,
        // Documentos que este proceso puntual exige — configurados por admin en
        // admin/catálogo → editar proceso (ver ProcesoService.obtenerDocumentosRequeridos). Viene
        // ya resuelto acá para no exponer un endpoint público nuevo solo para esto.
        List<DocumentoRequeridoResponse> documentosRequeridos
) {}
