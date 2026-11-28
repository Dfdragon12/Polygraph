package com.polygraph.erp.modules.auth.dto;

public record LoginResponse(
        String token,
        String refreshToken,
        UsuarioInfo usuario
) {
    public record UsuarioInfo(
            Long id,
            String email,
            String rol,
            String nombre,
            boolean requiereCambioPassword,
            String tipoCliente,
            // null para roles internos (no aplica). Para ADMIN_CLIENTE/ANALISTA_CLIENTE: false
            // hasta que el cliente completó su primera compra (prepago) o tiene un acuerdo de
            // crédito vigente (pospago) — el frontend usa esto para ocultar "Solicitudes" hasta
            // entonces. Se calcula solo, no hay ninguna aprobación manual (ver AuthService.
            // resolverPuedeSolicitar).
            Boolean puedeSolicitar
    ) {}
}
