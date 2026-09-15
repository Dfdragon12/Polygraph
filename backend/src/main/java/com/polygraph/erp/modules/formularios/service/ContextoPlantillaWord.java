package com.polygraph.erp.modules.formularios.service;

import java.util.List;
import java.util.Map;

/** Raíz de expresiones para los marcadores de una plantilla Word (office-stamper evalúa
 *  Spring Expression Language contra esto). Clase con getters normales a propósito — NO un
 *  record: SpEL resuelve propiedades vía getX(), que los records no generan.
 *
 *  En el .docx: ${candidato['CEDULA']}, ${campos['observacion']}, y sobre una fila de tabla
 *  comentada con repeatTableRow(tablas['clave_seccion']) — dentro de esa fila, cada celda usa
 *  las mismas claves de columna que en "campos" (ej. ${clave}, ver RepeatRowProcessor).
 *
 *  campos/tablas son Object (no String): una columna ARCHIVO con una sola imagen resuelve a
 *  pro.verron.officestamper.preset.Image en vez de texto — office-stamper la incrusta de verdad
 *  en el documento en vez de solo escribir el nombre del archivo (ver
 *  FormularioPlantillaWordService.valorColumna()). candidato se queda en String: nunca carga
 *  archivos, solo datos básicos del candidato. */
public class ContextoPlantillaWord {

    private final Map<String, String> candidato;
    private final Map<String, Object> campos;
    private final Map<String, List<Map<String, Object>>> tablas;

    public ContextoPlantillaWord(Map<String, String> candidato, Map<String, Object> campos,
                                  Map<String, List<Map<String, Object>>> tablas) {
        this.candidato = candidato;
        this.campos = campos;
        this.tablas = tablas;
    }

    public Map<String, String> getCandidato() {
        return candidato;
    }

    public Map<String, Object> getCampos() {
        return campos;
    }

    public Map<String, List<Map<String, Object>>> getTablas() {
        return tablas;
    }
}
