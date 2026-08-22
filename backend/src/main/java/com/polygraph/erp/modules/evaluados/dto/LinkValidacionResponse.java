package com.polygraph.erp.modules.evaluados.dto;

public record LinkValidacionResponse(
        Integer idServicio,
        String cedulaEvaluado,
        String nombresEvaluado,
        String apellidosEvaluado,
        String cargo,
        String proceso,
        boolean hojaVidaCompletada
) {}
