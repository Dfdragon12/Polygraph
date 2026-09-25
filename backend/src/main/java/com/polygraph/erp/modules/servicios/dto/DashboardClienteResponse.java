package com.polygraph.erp.modules.servicios.dto;

import java.util.List;

public record DashboardClienteResponse(
    long serviciosActivos,
    long serviciosPendientes,
    long serviciosFinalizadosMes,
    long serviciosDisponibles,
    List<SolicitudResumenResponse> ultimasSolicitudes,
    // Tipo de cliente en vivo (leído del cliente en BD, no del JWT cacheado en el navegador) — el
    // frontend lo usa para decidir si pide bolsa prepago o cupo pospago, evitando que un cambio de
    // tipo hecho por un admin quede invisible mientras el JWT del cliente (24h) siga vigente.
    String tipoCliente
) {}
