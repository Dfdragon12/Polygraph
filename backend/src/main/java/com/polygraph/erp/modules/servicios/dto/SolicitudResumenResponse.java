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
    LocalDate fechaEntregaEstimada,
    // Estado de tiempo en días hábiles (null si está cancelada o sin fecha).
    com.polygraph.erp.shared.dto.TiempoServicio tiempo
) {}
