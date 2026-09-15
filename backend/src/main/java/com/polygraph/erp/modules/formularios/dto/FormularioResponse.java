package com.polygraph.erp.modules.formularios.dto;

import java.util.List;
import java.util.Map;

public record FormularioResponse(
        Integer idFormulario,
        Long idServicioSubproceso,
        Integer idServicio,
        Long idCandidato,
        String nombreEvaluado,
        String cedulaEvaluado,
        Long idProfesionalEncargado,
        String nombreProfesionalEncargado,
        boolean soloLectura,
        List<CampoCandidatoValorResponse> datosCandidato,
        List<SeccionDatosResponse> secciones,
        List<TablaFijaResponse> tablasFijas,
        // Versión de la plantilla con la que se creó este subproceso vs. la versión actual — ver
        // TipoProgreso.versionPlantilla.
        Integer versionPlantilla,
        Integer versionPlantillaActual
) {
    /** valores solo aplica a secciones CAMPOS (idColumna -> valor); filas solo a secciones
     *  TABLA. El campo que no aplica llega vacío/null. orden: para intercalar en el mismo orden
     *  del constructor junto con tablasFijas (que vienen en una lista aparte). */
    public record SeccionDatosResponse(
            Integer idSeccion,
            String nombre,
            String tipo,
            Integer orden,
            List<ColumnaResponse> columnas,
            Map<Integer, String> valores,
            List<FilaDatosResponse> filas) {}

    public record FilaDatosResponse(Integer idFila, String etiqueta, Map<Integer, String> valores) {}

    /** Una tabla de la hoja de vida del candidato (referencias, educación, experiencia laboral,
     *  inactividades) — se muestra aquí intercalada en el orden del constructor, pero se EDITA
     *  aparte, directo contra el registro real del candidato (ver CandidatoHojaVidaController,
     *  con idCandidato de arriba y clave=origenFijo de esta sección) — no es una copia del
     *  servicio, es la información real y compartida de esa persona. */
    public record TablaFijaResponse(
            Integer idSeccion,
            String nombre,
            Integer orden,
            String clave,
            List<ColumnaFija> columnas,
            List<Map<String, String>> filas) {
        public record ColumnaFija(String clave, String etiqueta) {}
    }
}
