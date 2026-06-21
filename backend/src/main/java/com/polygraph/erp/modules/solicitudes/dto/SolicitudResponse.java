package com.polygraph.erp.modules.solicitudes.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SolicitudResponse(
        Long idSolicitud,
        String cedulaEvaluado,
        String nombresEvaluado,
        String apellidosEvaluado,
        String cargo,
        String ciudadEvaluado,
        String estado,
        LocalDateTime fechaSolicitud,
        LocalDate fechaEntregaEstimada,
        List<String> servicios
) {}
