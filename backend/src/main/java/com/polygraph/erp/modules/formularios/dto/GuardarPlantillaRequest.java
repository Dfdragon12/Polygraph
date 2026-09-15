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
            List<FilaSugeridaRequest> filasSugeridas) {}

    /** nombre es opcional: si viene vacío, el documento muestra solo el valor, sin etiqueta.
     *  campoCandidato/campoCliente no-nulo (mutuamente excluyentes) marca esta columna como un
     *  dato automático (se resuelve en vivo, no se guarda a mano) — ver CampoCandidato/
     *  CampoCliente. clave: identificador para marcadores de plantilla Word (${campos['clave']})
     *  — null/vacío = se autogenera del nombre; si se manda uno, se respeta (edición manual del
     *  admin). opciones: solo cuando tipoDato es "LISTA" — las opciones de la lista desplegable,
     *  en el orden en que las escribió el admin; se ignora para cualquier otro tipo de dato. */
    public record ColumnaRequest(Integer idColumna,
                                  @Size(max = 150, message = "El nombre de la columna no puede pasar de 150 caracteres.") String nombre,
                                  @NotBlank String tipoDato,
                                  String campoCandidato, String campoCliente, String clave, List<String> opciones) {}

    public record FilaSugeridaRequest(String etiqueta) {}
}
