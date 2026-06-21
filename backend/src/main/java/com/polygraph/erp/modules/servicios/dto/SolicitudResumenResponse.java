package com.polygraph.erp.modules.servicios.dto;

import com.polygraph.erp.shared.enums.EstadoServicio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SolicitudResumenResponse(
    Long idSolicitud,
    String cedulaEvaluado,
    String nombresEvaluado,
    String apellidosEvaluado,
    String cargo,
    String ciudadEvaluado,
    List<String> servicios,
    EstadoServicio estado,
    LocalDateTime fechaSolicitud,
    LocalDate fechaEntregaEstimada
) {}
