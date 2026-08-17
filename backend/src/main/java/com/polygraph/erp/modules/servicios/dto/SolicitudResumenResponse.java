package com.polygraph.erp.modules.servicios.dto;

import com.polygraph.erp.shared.enums.EstadoServicio;

import java.time.LocalDate;

public record SolicitudResumenResponse(
    Integer idServicio,
    String cedulaEvaluado,
    String nombresEvaluado,
    String apellidosEvaluado,
    String cargo,
    String proceso,
    EstadoServicio estado,
    LocalDate fechaSolicitud,
    LocalDate fechaEntregaEstimada
) {}
