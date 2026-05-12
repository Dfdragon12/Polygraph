package com.polygraph.erp.modules.evaluados.dto;

import java.util.List;

public record LinkValidacionResponse(
        Long idSolicitud,
        String cedulaEvaluado,
        String nombresEvaluado,
        String apellidosEvaluado,
        String cargo,
        List<String> servicios,
        boolean hojaVidaCompletada
) {}
