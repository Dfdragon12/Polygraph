package com.polygraph.erp.modules.servicios.dto;

import java.util.List;

public record DashboardClienteResponse(
    long serviciosActivos,
    long serviciosPendientes,
    // Informes (subprocesos) publicados en el mes en curso, hora de Colombia.
    long publicadosMes,
    long serviciosDisponibles,
    List<SolicitudResumenResponse> ultimasSolicitudes,
    // Tipo de cliente en vivo (leído del cliente en BD, no del JWT cacheado en el navegador) — el
    // frontend lo usa para decidir si pide bolsa prepago o cupo pospago, evitando que un cambio de
    // tipo hecho por un admin quede invisible mientras el JWT del cliente (24h) siga vigente.
    String tipoCliente,
    // Solicitudes abiertas con la fecha de entrega ya pasada (días hábiles).
    long serviciosVencidos
) {}
