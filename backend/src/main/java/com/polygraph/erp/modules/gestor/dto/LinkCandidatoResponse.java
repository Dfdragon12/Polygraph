package com.polygraph.erp.modules.gestor.dto;

import java.time.LocalDateTime;

public record LinkCandidatoResponse(
        Long id,
        Integer idServicio,
        String nombresEvaluado,
        String apellidosEvaluado,
        String cedulaEvaluado,
        String cargo,
        String estado,
        String tokenMascarado,
        String tokenCompleto,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaExpiracion,
        LocalDateTime fechaUso,
        String ipOrigen,
        LocalDateTime fechaPrimerIngreso,
        Integer progresoFormulario,
        Integer pasoActual,
        Short intentosFallidos,
        String generadoPorNombre,
        String proceso
) {}
