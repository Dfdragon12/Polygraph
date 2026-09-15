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
 *  comentario no es una instrucción que el procesador reconozca, no hace nada. */
public record AnalisisPlantillaWordResponse(List<String> marcadoresDesconocidos, List<String> camposSinUsar,
                                             List<String> tablasMalUsadas) {}
