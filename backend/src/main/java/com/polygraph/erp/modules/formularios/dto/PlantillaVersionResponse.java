package com.polygraph.erp.modules.formularios.dto;

import java.time.LocalDateTime;

/** Una versión (actual o histórica) del .docx con marcadores de un subproceso — idHistorial es
 *  null cuando es la versión ACTUAL (se descarga con GET .../plantilla-word, no con
 *  .../plantilla-word/historial/{id}). */
public record PlantillaVersionResponse(Long idHistorial, String nombreArchivo, LocalDateTime fechaCarga,
                                        String subidoPor, boolean actual) {}
