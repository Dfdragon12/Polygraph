package com.polygraph.erp.modules.gestor.dto;

import java.util.List;

/** Todo lo que necesita la pantalla de novedades de un subproceso en una sola llamada: las
 *  novedades en orden cronológico, los tipos que aplican a ESTE subproceso (según su rol
 *  responsable) y qué puede hacer quien consulta. */
public record NovedadesSubprocesoResponse(
        List<NovedadSubprocesoResponse> novedades,
        List<TipoNovedadResponse> tipos,
        boolean puedeRegistrar,
        boolean puedeResolver,
        // Puede agregar observaciones a las novedades abiertas: quien ejecuta (sobre las suyas) o
        // quien coordina.
        boolean puedeComentar,
        // Solo con puedeResolver: si al resolver se puede además correr la hora del subproceso
        // (sigue en curso) — y cuál es la hora actual, para precargar el selector.
        boolean puedeReprogramar,
        java.time.LocalDateTime fechaProgramadaActual
) {
    /** Una opción de la lista "¿Qué pasó?" — del catálogo tipos_novedad, ya filtrada por el rol
     *  del subproceso y solo activas. textoAyuda: ejemplo para el campo Detalle. */
    public record TipoNovedadResponse(Integer idTipoNovedad, String etiqueta, String categoria, String categoriaEtiqueta,
                                      String categoriaColor, String textoAyuda, String clave) {}
}
