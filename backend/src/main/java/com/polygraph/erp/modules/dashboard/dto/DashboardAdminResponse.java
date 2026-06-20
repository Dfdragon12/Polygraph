package com.polygraph.erp.modules.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardAdminResponse(
        long totalActivos,
        long internosActivos,
        long clientesActivos,
        List<UltimoLoginDto> ultimosLogins,
        List<ServicioCategoriaDto> serviciosPorCategoria
) {
    public record UltimoLoginDto(
            Long idUsuario,
            String nombre,
            String apellido,
            String email,
            String rol,
            Boolean activo,
            LocalDateTime ultimoAcceso
    ) {}

    public record ServicioCategoriaDto(
            String categoria,
            long total,
            long activos
    ) {}
}
