package com.polygraph.erp.modules.gestor.dto;

public record EmpleadoAsignableResponse(
        Long idUsuario,
        String nombre,
        String apellido,
        String rol
) {}
