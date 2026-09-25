package com.polygraph.erp.modules.formularios.dto;

import java.util.List;

/** campoCandidato/campoCliente no-nulo (mutuamente excluyentes) = esta columna muestra un dato
 *  automático (candidato o cliente, se resuelve en vivo, el analista no la puede editar); ambos
 *  null = columna manual normal. clave = identificador estable para usar en marcadores de
 *  plantilla Word (${campos['clave']}). opciones solo aplica cuando tipoDato es LISTA — las
 *  opciones de la lista desplegable, en orden; vacía para cualquier otro tipo. */
public record ColumnaResponse(Integer idColumna, String nombre, String tipoDato, Integer orden,
                               String campoCandidato, String campoCliente, String clave, List<String> opciones,
                               // Calificación de riesgo. puntajes: el constructor (admin) y el formulario
                               // (para sumar los puntos por fila, decisión 25-sep-2026); null en
                               // /estructura. opcionesCriticas va a todos: al elegir una de estas
                               // respuestas se pide una justificación.
                               boolean calificaRiesgo,
                               List<PuntajeOpcion> puntajes,
                               List<String> opcionesCriticas) {

    public record PuntajeOpcion(String opcion, java.math.BigDecimal puntos, boolean esCritico) {}

    /** Único punto donde se arma — así ningún llamador se olvida de ocultar los puntos. */
    public static ColumnaResponse de(com.polygraph.erp.modules.formularios.entity.FormularioColumna c,
                                     List<String> opciones, boolean incluirPuntajes) {
        boolean califica = Boolean.TRUE.equals(c.getCalificaRiesgo());
        List<PuntajeOpcion> puntajes = incluirPuntajes && califica
                ? c.getPuntajes().stream().map(p -> new PuntajeOpcion(p.getOpcion(), p.getPuntos(), Boolean.TRUE.equals(p.getEsCritico()))).toList()
                : (incluirPuntajes ? List.of() : null);
        List<String> criticas = califica
                ? c.getPuntajes().stream().filter(p -> Boolean.TRUE.equals(p.getEsCritico())).map(p -> p.getOpcion()).toList()
                : List.of();
        return new ColumnaResponse(c.getIdColumna(), c.getNombre(), c.getTipoDato().name(), c.getOrden(),
                c.getCampoCandidato(), c.getCampoCliente(), c.getClave(), opciones, califica, puntajes, criticas);
    }
}
