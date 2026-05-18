package com.polygraph.erp.modules.solicitudes.dto;

import java.util.List;

public record BulkUploadResponse(
        int filasExitosas,
        int filasConError,
        List<ErrorFila> errores
) {
    public record ErrorFila(int fila, String cedula, String error) {}
}
