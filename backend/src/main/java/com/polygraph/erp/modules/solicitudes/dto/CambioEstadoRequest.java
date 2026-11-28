package com.polygraph.erp.modules.solicitudes.dto;

import jakarta.validation.constraints.NotBlank;

/** Cambio de estado del SERVICIO (hoy solo: publicar el estudio completo, o cancelar la solicitud). Al cancelar, el gestor y el
 *  administrador eligen el motivo de la lista de cancelación; el cliente escribe el suyo en la observación. */
public record CambioEstadoRequest(
        @NotBlank(message = "El estado es obligatorio")
        String estado,

        String observacion,

        Integer idMotivo
) {
    public CambioEstadoRequest(String estado, String observacion) {
        this(estado, observacion, null);
    }
}
