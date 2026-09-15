package com.polygraph.erp.modules.formularios.dto;

import java.util.List;

/** campoCandidato/campoCliente no-nulo (mutuamente excluyentes) = esta columna muestra un dato
 *  automático (candidato o cliente, se resuelve en vivo, el analista no la puede editar); ambos
 *  null = columna manual normal. clave = identificador estable para usar en marcadores de
 *  plantilla Word (${campos['clave']}). opciones solo aplica cuando tipoDato es LISTA — las
 *  opciones de la lista desplegable, en orden; vacía para cualquier otro tipo. */
public record ColumnaResponse(Integer idColumna, String nombre, String tipoDato, Integer orden,
                               String campoCandidato, String campoCliente, String clave, List<String> opciones) {}
