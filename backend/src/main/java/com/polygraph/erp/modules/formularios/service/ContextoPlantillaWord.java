package com.polygraph.erp.modules.formularios.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    /** Calificación de riesgo (docs/modelo-calificacion-riesgo.md §7): ${riesgo['nivel']} /
     *  ${riesgo['puntaje']} del estudio completo, y ${calificacion['nivel']} /
     *  ${calificacion['puntaje']} del subproceso de este documento. Siempre traen las dos claves
     *  (vacías si no aplica), para que el marcador nunca rompa la generación. */
    private final Map<String, String> riesgo;
    private final Map<String, String> calificacion;
    /** Claves de las secciones opcionales que quien diligencia quitó (V212). */
    private Set<String> seccionesOmitidas = Set.of();

    public ContextoPlantillaWord(Map<String, String> candidato, Map<String, Object> campos,
                                  Map<String, List<Map<String, Object>>> tablas) {
        this(candidato, campos, tablas, MARCADORES_VACIOS, MARCADORES_VACIOS);
    }

    public ContextoPlantillaWord(Map<String, String> candidato, Map<String, Object> campos,
                                  Map<String, List<Map<String, Object>>> tablas,
                                  Map<String, String> riesgo, Map<String, String> calificacion) {
        this.candidato = candidato;
        this.campos = campos;
        this.tablas = tablas;
        this.riesgo = riesgo != null ? riesgo : MARCADORES_VACIOS;
        this.calificacion = calificacion != null ? calificacion : MARCADORES_VACIOS;
    }

    public static final Map<String, String> MARCADORES_VACIOS = Map.of("nivel", "", "puntaje", "");

    /** Imágenes de las columnas ARCHIVO de tablas: no se incrustan en la celda (sale "Ver soporte N")
     *  sino en un anexo al final — ver SoportesAnexoWord. No es un marcador del Word. */
    private final List<SoportesAnexoWord.Soporte> soportes = new java.util.ArrayList<>();

    List<SoportesAnexoWord.Soporte> soportesAnexo() {
        return soportes;
    }

    public Map<String, String> getRiesgo() {
        return riesgo;
    }

    public Map<String, String> getCalificacion() {
        return calificacion;
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

    /** Devuelve este mismo contexto con las secciones que se quitaron (claves). */
    public ContextoPlantillaWord conSeccionesOmitidas(Set<String> claves) {
        this.seccionesOmitidas = claves != null ? claves : Set.of();
        return this;
    }

    /** Bloque condicional de una sección en el Word: se selecciona TODO lo de la sección (título,
     *  textos, tablas) y se le pone el comentario repeatDocPart(secciones['clave']). Una sección
     *  incluida vale una lista con este mismo contexto (el bloque sale UNA vez y adentro siguen
     *  funcionando los marcadores de siempre); una quitada vale una lista vacía y el bloque
     *  desaparece. Una clave que no existe se trata como incluida, para que un error de tipeo
     *  nunca borre parte del informe (Analizar la marca como desconocida). */
    public Map<String, List<Object>> getSecciones() {
        ContextoPlantillaWord raiz = this;
        return new HashMap<>() {
            @Override
            public List<Object> get(Object clave) {
                return seccionesOmitidas.contains(String.valueOf(clave)) ? List.of() : List.of(raiz);
            }

            @Override
            public boolean containsKey(Object clave) {
                return true;
            }
        };
    }
}
