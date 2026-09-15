package com.polygraph.erp.modules.formularios.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/** Guarda el formulario completo de una sola vez — reemplaza por completo lo que había
 *  (filas y datos), mismo patrón que el resto del sistema para evitar estados intermedios
 *  inconsistentes. */
public record GuardarFormularioRequest(List<SeccionDatosRequest> secciones) {

    public record SeccionDatosRequest(
            @NotNull Integer idSeccion,
            Map<Integer, String> valores,
            List<FilaDatosRequest> filas) {}

    public record FilaDatosRequest(String etiqueta, Map<Integer, String> valores) {}
}
