package com.polygraph.erp.modules.dashboard.dto;

import java.util.List;

public record ServiciosPorEstadoResponse(
        List<ClienteResumenDto> clientes,
        List<EstadoDto> estados,
        long totalServicios
) {
    public record ClienteResumenDto(Integer idCliente, String nombre) {}

    public record EstadoDto(String estado, long total) {}
}
