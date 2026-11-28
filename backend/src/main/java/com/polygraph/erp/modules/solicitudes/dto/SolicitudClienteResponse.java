package com.polygraph.erp.modules.solicitudes.dto;

import java.util.List;

/** Lo que el cliente diligenció al pedir una poligrafía: el formato adicional (si el proceso lo
 *  tiene) y los temas que pidió evaluar. Lo ven el poligrafista y quien coordina, en la ficha del
 *  candidato. `titulo` es null si la solicitud no tiene formato adicional. */
public record SolicitudClienteResponse(
        String titulo,
        List<Dato> datos,
        List<Tema> temas
) {
    public record Dato(String etiqueta, String valor) {}

    public record Tema(String nombre, String detalle) {}
}
