package com.polygraph.erp.modules.dashboard.dto;

import java.util.List;

public record ServiciosPorEstadoResponse(
        List<ClienteResumenDto> clientes,
        List<EstadoDto> estados,
        long totalServicios,
        // Abiertas con la fecha de entrega ya pasada (días hábiles); cruza los estados, no es uno de ellos.
        long vencidas
) {
    public record ClienteResumenDto(Integer idCliente, String nombre) {}

    public record EstadoDto(String estado, long total) {}
}
