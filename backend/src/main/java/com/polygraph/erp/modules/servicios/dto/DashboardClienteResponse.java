package com.polygraph.erp.modules.servicios.dto;

import java.util.List;

public record DashboardClienteResponse(
    long serviciosActivos,
    long serviciosPendientes,
    long serviciosFinalizadosMes,
    long serviciosDisponibles,
    List<SolicitudResumenResponse> ultimasSolicitudes
) {}
