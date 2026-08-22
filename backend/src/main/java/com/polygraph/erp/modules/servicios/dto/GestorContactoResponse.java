package com.polygraph.erp.modules.servicios.dto;

public record GestorContactoResponse(
    String nombreCompleto,
    String telefono,
    String email
) {}
