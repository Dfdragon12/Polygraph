package com.polygraph.erp.modules.solicitudes.dto;

import com.polygraph.erp.modules.solicitudes.formato.FormatoSolicitud;

import java.util.List;

/** Un formato adicional con sus campos, para que el cliente lo dibuje al solicitar. */
public record FormatoSolicitudResponse(
        String formato,
        String titulo,
        List<CampoResponse> campos
) {
    public record CampoResponse(String clave, String etiqueta, String tipo, boolean obligatorio, String ayuda) {}

    public static FormatoSolicitudResponse de(FormatoSolicitud f) {
        return new FormatoSolicitudResponse(f.name(), f.titulo(), f.campos().stream()
                .map(c -> new CampoResponse(c.clave(), c.etiqueta(), c.tipo().name(), c.obligatorio(), c.ayuda()))
                .toList());
    }
}
