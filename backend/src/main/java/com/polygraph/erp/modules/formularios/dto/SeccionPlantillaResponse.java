package com.polygraph.erp.modules.formularios.dto;

import java.util.List;

/** Una sección de la plantilla de un tipo de subproceso — usada tanto por admin/catálogo →
 *  Plantilla (para editarla) como por el formulario del analista (para saber qué mostrar). */
public record SeccionPlantillaResponse(
        Integer idSeccion,
        String nombre,
        String tipo,
        Integer orden,
        String clave,
        String origenFijo,
        List<String> columnasOcultas,
        List<ColumnaResponse> columnas,
        List<FilaSugeridaResponse> filasSugeridas,
        boolean calculada,
        /** Quien diligencia puede quitarla de su formulario (V212). */
        boolean opcional,
        /** Grupo con el que se clasifica la sección (V217); null = sin grupo. */
        String grupo
) {}
