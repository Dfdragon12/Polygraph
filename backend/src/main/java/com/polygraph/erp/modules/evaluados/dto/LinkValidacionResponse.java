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
        boolean hojaVidaCompletada,
        // Documentos que este proceso puntual exige — configurados por admin en
        // admin/catálogo → editar proceso (ver ProcesoService.obtenerDocumentosRequeridos). Viene
        // ya resuelto acá para no exponer un endpoint público nuevo solo para esto.
        List<DocumentoRequeridoResponse> documentosRequeridos
) {}
