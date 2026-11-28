package com.polygraph.erp.modules.formularios.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/** Guarda el formulario completo de una sola vez — reemplaza por completo lo que había
 *  (filas y datos), mismo patrón que el resto del sistema para evitar estados intermedios
 *  inconsistentes. */
public record GuardarFormularioRequest(List<SeccionDatosRequest> secciones,
                                       /** Secciones opcionales que quien diligencia quitó (V212); null = ninguna. */
                                       List<Integer> seccionesOmitidas) {

    public GuardarFormularioRequest(List<SeccionDatosRequest> secciones) {
        this(secciones, null);
    }

    /** justificaciones: idColumna -> explicación de una respuesta CRÍTICA (campo calificable). */
    public record SeccionDatosRequest(
            @NotNull Integer idSeccion,
            Map<Integer, String> valores,
            List<FilaDatosRequest> filas,
            Map<Integer, String> justificaciones) {
        public SeccionDatosRequest(Integer idSeccion, Map<Integer, String> valores, List<FilaDatosRequest> filas) {
            this(idSeccion, valores, filas, null);
        }
    }

    // idOrigenFijo: null = fila manual normal de una sección TABLA (comportamiento de siempre).
    // No null = esta fila es la VALIDACIÓN de analista/poligrafista/visitador correlacionada con
    // esa fila real de la hoja de vida del candidato (ver FormularioFila.idOrigenFijo) — solo
    // aplica a secciones con origenFijo.
    public record FilaDatosRequest(String etiqueta, Map<Integer, String> valores, Long idOrigenFijo,
                                   Map<Integer, String> justificaciones) {
        public FilaDatosRequest(String etiqueta, Map<Integer, String> valores, Long idOrigenFijo) {
            this(etiqueta, valores, idOrigenFijo, null);
        }
    }
}
