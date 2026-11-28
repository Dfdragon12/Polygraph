package com.polygraph.erp.modules.formularios.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Actualiza la plantilla (secciones + columnas + filas sugeridas) de un tipo de subproceso.
 *  idSeccion/idColumna identifican una sección/columna EXISTENTE que se debe conservar (se
 *  actualiza en el sitio); si vienen null, es una nueva. Una sección o columna existente que
 *  ya tenía datos guardados y no viene en esta lista se ARCHIVA, no se borra — ver
 *  FormularioPlantillaService.guardar(). */
public record GuardarPlantillaRequest(@Valid List<SeccionRequest> secciones) {

    /** clave: identificador para marcadores de plantilla Word (${tablas['clave']} en secciones
     *  TABLA) — null/vacío = se autogenera del nombre; si se manda uno, se respeta (edición
     *  manual del admin); se ignora si origenFijo no es null (la clave queda igual a origenFijo).
     *  origenFijo: solo para secciones TABLA — null = el analista llena las filas a mano
     *  (de siempre); una de CandidatoInfoService.CLAVES_TABLAS_FIJAS ("referencias", "educacion",
     *  "experiencia_laboral", "inactividades") = la tabla se llena sola con la hoja de vida del
     *  candidato. */
    public record SeccionRequest(
            Integer idSeccion,
            @NotBlank @Size(max = 150, message = "El nombre de la sección no puede pasar de 150 caracteres.") String nombre,
            @NotBlank String tipo,
            String clave,
            String origenFijo,
            List<String> columnasOcultas,
            @Valid List<ColumnaRequest> columnas,
            List<FilaSugeridaRequest> filasSugeridas,
            /** Solo TABLA: las filas las arma el código del subservicio, no el analista. */
            Boolean calculada,
            /** Quien diligencia puede quitarla de su formulario (V212); null/false = obligatoria. */
            Boolean opcional,
            /** Grupo con el que se clasifica la sección (V217); vacío = sin grupo. */
            @Size(max = 60, message = "El grupo no puede pasar de 60 caracteres.") String grupo) {

        public SeccionRequest(Integer idSeccion, String nombre, String tipo, String clave, String origenFijo,
                              List<String> columnasOcultas, List<ColumnaRequest> columnas, List<FilaSugeridaRequest> filasSugeridas,
                              Boolean calculada, Boolean opcional) {
            this(idSeccion, nombre, tipo, clave, origenFijo, columnasOcultas, columnas, filasSugeridas, calculada, opcional, null);
        }

        public SeccionRequest(Integer idSeccion, String nombre, String tipo, String clave, String origenFijo,
                              List<String> columnasOcultas, List<ColumnaRequest> columnas, List<FilaSugeridaRequest> filasSugeridas) {
            this(idSeccion, nombre, tipo, clave, origenFijo, columnasOcultas, columnas, filasSugeridas, null, null);
        }

        public SeccionRequest(Integer idSeccion, String nombre, String tipo, String clave, String origenFijo,
                              List<String> columnasOcultas, List<ColumnaRequest> columnas, List<FilaSugeridaRequest> filasSugeridas,
                              Boolean calculada) {
            this(idSeccion, nombre, tipo, clave, origenFijo, columnasOcultas, columnas, filasSugeridas, calculada, null);
        }
    }

    /** nombre es opcional: si viene vacío, el documento muestra solo el valor, sin etiqueta.
     *  campoCandidato/campoCliente no-nulo (mutuamente excluyentes) marca esta columna como un
     *  dato automático (se resuelve en vivo, no se guarda a mano) — ver CampoCandidato/
     *  CampoCliente. clave: identificador para marcadores de plantilla Word (${campos['clave']})
     *  — null/vacío = se autogenera del nombre; si se manda uno, se respeta (edición manual del
     *  admin). opciones: solo cuando tipoDato es "LISTA" — las opciones de la lista desplegable,
     *  en el orden en que las escribió el admin; se ignora para cualquier otro tipo de dato. */
    /** calificaRiesgo/puntajes: calificación de riesgo (solo Lista o Sí/No) — puntos y marca
     *  crítica por respuesta; en Sí/No la opción es "true" o "false". */
    public record ColumnaRequest(Integer idColumna,
                                  @Size(max = 150, message = "El nombre de la columna no puede pasar de 150 caracteres.") String nombre,
                                  @NotBlank String tipoDato,
                                  String campoCandidato, String campoCliente, String clave, List<String> opciones,
                                  Boolean calificaRiesgo,
                                  @Valid List<PuntajeOpcionRequest> puntajes,
                                  /** Dato del estudio (CampoServicio) — excluyente con campoCandidato/campoCliente. */
                                  String campoServicio,
                                  /** Lo llena el código del subservicio; el analista solo lo ve. */
                                  Boolean calculado,
                                  @Size(max = 200, message = "El valor por defecto no puede pasar de 200 caracteres.") String valorDefecto,
                                  Boolean obligatorio,
                                  /** Calificación por hallazgos (V205); null = campo normal. */
                                  CalificacionColumnaDto calificacion,
                                  /** Máximo de caracteres de un campo de texto (V209); null = el de por defecto. */
                                  Integer longitudMaxima) {

        public ColumnaRequest(Integer idColumna, String nombre, String tipoDato, String campoCandidato, String campoCliente,
                              String clave, List<String> opciones, Boolean calificaRiesgo, List<PuntajeOpcionRequest> puntajes,
                              String campoServicio, Boolean calculado, String valorDefecto, Boolean obligatorio,
                              CalificacionColumnaDto calificacion) {
            this(idColumna, nombre, tipoDato, campoCandidato, campoCliente, clave, opciones, calificaRiesgo, puntajes,
                    campoServicio, calculado, valorDefecto, obligatorio, calificacion, null);
        }

        public ColumnaRequest(Integer idColumna, String nombre, String tipoDato, String campoCandidato, String campoCliente,
                              String clave, List<String> opciones, Boolean calificaRiesgo, List<PuntajeOpcionRequest> puntajes) {
            this(idColumna, nombre, tipoDato, campoCandidato, campoCliente, clave, opciones, calificaRiesgo, puntajes,
                    null, null, null, null, null, null);
        }

        public ColumnaRequest(Integer idColumna, String nombre, String tipoDato, String campoCandidato, String campoCliente,
                              String clave, List<String> opciones, Boolean calificaRiesgo, List<PuntajeOpcionRequest> puntajes,
                              String campoServicio, Boolean calculado, String valorDefecto, Boolean obligatorio) {
            this(idColumna, nombre, tipoDato, campoCandidato, campoCliente, clave, opciones, calificaRiesgo, puntajes,
                    campoServicio, calculado, valorDefecto, obligatorio, null, null);
        }
    }

    public record PuntajeOpcionRequest(
            String opcion,
            @jakarta.validation.constraints.DecimalMin(value = "0", message = "Los puntos no pueden ser negativos.")
            @jakarta.validation.constraints.DecimalMax(value = "100", message = "Una respuesta no puede valer más de 100 puntos.")
            java.math.BigDecimal puntos,
            Boolean esCritico) {}

    public record FilaSugeridaRequest(String etiqueta) {}
}
