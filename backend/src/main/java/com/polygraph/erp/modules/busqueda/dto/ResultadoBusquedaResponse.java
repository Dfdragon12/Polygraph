package com.polygraph.erp.modules.busqueda.dto;

/** Un resultado del buscador global — heterogéneo a propósito (cliente, candidato, servicio,
 *  empleado son cosas distintas), unificado solo para mostrarlos en la misma lista agrupados por
 *  `tipo`. `ruta` ya viene resuelta (a veces con `?q=` para que la pantalla de destino filtre
 *  sola, a veces directo al detalle) — el frontend solo navega, no arma la URL. */
public record ResultadoBusquedaResponse(
        String tipo,
        String titulo,
        String subtitulo,
        String ruta,
        String icono
) {
}
