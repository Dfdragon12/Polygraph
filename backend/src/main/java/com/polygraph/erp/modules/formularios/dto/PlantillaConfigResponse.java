package com.polygraph.erp.modules.formularios.dto;

import java.time.LocalDateTime;

/** plantillaWordRuta: ruta del .docx con marcadores subido para este subproceso — null si el
 *  administrador todavía no ha subido ninguno. fechaCarga/subidoPor: null en plantillas subidas
 *  antes de que se empezara a registrar esto (V170). versionPlantilla: contador que sube cada
 *  vez que se guardan cambios de secciones/columnas — ver TipoProgreso.versionPlantilla. */
public record PlantillaConfigResponse(Integer idTipoProgreso, String plantillaWordRuta,
                                       LocalDateTime fechaCarga, String subidoPor, Integer versionPlantilla) {}
