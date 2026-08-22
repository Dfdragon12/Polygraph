package com.polygraph.erp.modules.solicitudes.dto;

import java.time.LocalDateTime;

public record ReversionSolicitudResponse(
        Long id,
        Integer idServicio,
        String cedulaEvaluado,
        String nombresEvaluado,
        String apellidosEvaluado,
        String cargo,
        String nombreCliente,
        String proceso,
        String estadoActualServicio,
        String estadoDeseado,
        String motivo,
        String estado,
        String solicitadoPorNombre,
        LocalDateTime fechaSolicitud,
        String revisadoPorNombre,
        LocalDateTime fechaRevision,
        String comentarioRevision
) {}
