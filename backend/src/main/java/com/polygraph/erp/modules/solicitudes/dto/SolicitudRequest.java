package com.polygraph.erp.modules.solicitudes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

public record SolicitudRequest(
        @NotBlank(message = "La cédula es obligatoria")
        String cedula,

        @NotBlank(message = "Los nombres son obligatorios")
        String nombres,

        @NotBlank(message = "Los apellidos son obligatorios")
        String apellidos,

        String celular,
        String email,
        Integer idCiudad,
        String cargo,

        // NS-07: ESTANDAR | SENSIBLE | CRITICO. Obligatoria al crear desde la pantalla; el motor de riesgo la toma de aquí (Servicio.criticidadCargo).
        String criticidadCargo,

        // NS-08: dirección de la visita; obligatoria si algún servicio elegido la requiere (Proceso.requiereDireccion).
        String direccionVisita,

        @NotEmpty(message = "Debe seleccionar al menos un proceso")
        List<Integer> procesosIds,

        String notas,

        // Respuestas al formato adicional del proceso (pre-empleo, rutina, específica): clave → valor.
        // Las claves y qué es obligatorio las define FormatoSolicitud.
        Map<String, String> datosAdicionales,

        // Temas a evaluar en una poligrafía (máximo 4, del catálogo administrable).
        List<TemaPoligrafiaRequest> temasPoligrafia
) {
    /** Compatibilidad: una solicitud sin formato adicional ni temas (carga masiva, pruebas). */
    public SolicitudRequest(String cedula, String nombres, String apellidos, String celular, String email,
                            Integer idCiudad, String cargo, List<Integer> procesosIds, String notas) {
        this(cedula, nombres, apellidos, celular, email, idCiudad, cargo, null, null, procesosIds, notas, null, null);
    }
}
