package com.polygraph.erp.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "El refresh token es requerido")
        String refreshToken
) {}
