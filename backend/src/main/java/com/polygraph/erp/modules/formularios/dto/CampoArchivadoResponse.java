package com.polygraph.erp.modules.formularios.dto;

/** Una sección o columna archivada (activo = false) de la plantilla — el administrador la quitó
 *  en algún momento porque ya tenía respuestas guardadas. tipo = "SECCION" cuando es la sección
 *  completa (idColumna/nombreColumna/tipoDato van null); tipo = "COLUMNA" cuando es un campo
 *  suelto dentro de una sección que sigue activa. */
public record CampoArchivadoResponse(
        String tipo,
        Integer idSeccion,
        String nombreSeccion,
        Integer idColumna,
        String nombreColumna,
        String tipoDato) {
}
