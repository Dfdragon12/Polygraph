package com.polygraph.erp.modules.formularios.dto;

import java.util.List;

/** Compara los marcadores encontrados en el .docx subido contra los campos definidos en el
 *  constructor. marcadoresDesconocidos: aparecen en el documento pero no corresponden a ningún
 *  campo/candidato actual (probable error de tipeo, o campo que ya no existe). camposSinUsar:
 *  están definidos en el constructor pero no se encontró ningún marcador que los use en el
 *  documento (probable olvido al diseñar el .docx). tablasMalUsadas: un marcador tablas['clave']
 *  que NO va a repetir ninguna fila — dos causas posibles: (1) escrito directo en el texto del
 *  documento en vez de en un comentario, sale como texto plano de la lista completa (ej.
 *  "[{...}, {...}]"); o (2) escrito DENTRO de un comentario pero con la sintaxis de marcador
 *  (${tablas['clave']}) en vez de la función real (repeatTableRow(tablas['clave'])) — ese
 *  comentario no es una instrucción que el procesador reconozca, no hace nada. errorGeneracion:
 *  null si un ensayo real de generación (con datos de ejemplo, igual que la vista previa) no
 *  tuvo problemas; si no es null, es el mensaje real del error — la comparación de marcadores de
 *  arriba es solo por nombre de texto y a propósito NO valida marcadores sueltos ${['clave']}
 *  (los de dentro de una fila repetida — no se puede saber de forma confiable si están bien
 *  ubicados solo mirando el texto), así que este campo es la única forma de detectar ESE tipo de
 *  error sin tener que ir a "Vista previa" a descubrirlo ahí. */
public record AnalisisPlantillaWordResponse(List<String> marcadoresDesconocidos, List<String> camposSinUsar,
                                             List<String> tablasMalUsadas, String errorGeneracion) {}
