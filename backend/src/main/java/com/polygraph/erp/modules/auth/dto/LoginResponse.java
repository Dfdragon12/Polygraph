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
            boolean requiereCambioPassword
    ) {}
}
