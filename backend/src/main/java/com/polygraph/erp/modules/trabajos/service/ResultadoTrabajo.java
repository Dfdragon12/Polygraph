package com.polygraph.erp.modules.trabajos.service;

/** Lo que deja un trabajo terminado: un dato pequeño (se guarda como JSON en la fila) y/o un
 *  archivo en disco (solo la ruta relativa va a la BD, el contenido nunca). */
public record ResultadoTrabajo(Object datos, String rutaArchivo, String nombreArchivo, String tipoContenido) {

    public static ResultadoTrabajo datos(Object datos) {
        return new ResultadoTrabajo(datos, null, null, null);
    }

    public static ResultadoTrabajo archivo(String rutaArchivo, String nombreArchivo, String tipoContenido) {
        return new ResultadoTrabajo(null, rutaArchivo, nombreArchivo, tipoContenido);
    }
}
