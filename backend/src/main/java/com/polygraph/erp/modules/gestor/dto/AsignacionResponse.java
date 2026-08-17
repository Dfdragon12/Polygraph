package com.polygraph.erp.modules.gestor.dto;

import java.time.LocalDateTime;

public record AsignacionResponse(
        Long id,
        Integer idServicio,
        String nombresEvaluado,
        String apellidosEvaluado,
        String cedulaEvaluado,
        String cargo,
        String proceso,
        Integer idTipoProgreso,
        String subproceso,
        Integer duracionMinutos,
        String estado,
        Long idUsuarioAsignado,
        String nombreAsignado,
        LocalDateTime fechaProgramada,
        LocalDateTime fechaAsignacion,
        LocalDateTime fechaCompletado,
        String observaciones
) {}
