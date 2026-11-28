package com.polygraph.erp.modules.solicitudes.dto;

import java.util.List;

/** Resultado de la carga masiva (NS-12, todo o nada): o se crean todas las solicitudes, o no se crea ninguna y `errores` explica cada fila. */
public record BulkUploadResponse(
        int filasExitosas,
        int filasConError,
        List<ErrorFila> errores,
        String mensaje
) {
    /** fila 0 = problema de todo el archivo (por ejemplo, la mora del cliente). */
    public record ErrorFila(int fila, String cedula, String error) {}
}
