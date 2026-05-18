package com.polygraph.erp.modules.servicios.dto;

import com.polygraph.erp.shared.enums.EstadoServicio;

import java.time.LocalDate;

public record SolicitudResumenResponse(
    Integer idServicio,
    String tipoProceso,
    EstadoServicio estado,
    LocalDate fechaSolicitud,
    LocalDate fechaEntregaEstudio,
    String nombreCandidato
) {}
