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
        Integer versionPlantillaActual,
        // Estado del subproceso (ASIGNADO/COMPLETADO/PUBLICADO/CANCELADO) — el frontend lo usa
        // para decidir si mostrar "Aprobar"/"Devolver" (solo GESTOR/PROGRAMADOR, solo si sigue
        // COMPLETADO — una vez PUBLICADO ya no hay nada más que hacer aquí).
        String estado,
        // Nombre del .docx vinculado a este subproceso — el congelado (ServicioSubproceso.
        // rutaWordCongelada) si ya está COMPLETADO/PUBLICADO, o el actual si sigue en progreso.
        // null si nunca se ha subido un Word para este tipo de subproceso.
        String nombreArchivoWord,
        // Encabezado del formulario: qué subproceso es y para cuándo quedó programado (null si
        // es un subproceso virtual sin fecha, p.ej. de analista).
        String nombreSubproceso,
        java.time.LocalDateTime fechaProgramada,
        // Última observación de quien coordina (ServicioSubproceso.observaciones) — la que dejó
        // al asignar, o el motivo si lo devolvió para corrección. Se muestra arriba del formulario.
        String observacionesAsignacion,
        // Contacto del evaluado para el encabezado: celular (o el fijo si no tiene celular) y
        // ciudad de residencia. "" si no lo tiene registrado.
        String telefonoEvaluado,
        String ciudadEvaluado,
        // Qué servicio se está realizando (el proceso que compró el cliente, ej. "Estudio de
        // seguridad") y el cargo al que aspira el evaluado — para el encabezado del formulario.
        String nombreProceso,
        String cargo,
        // Para el tiempo de ejecución del encabezado: cuándo se asignó, cuándo se completó,
        // cuánto debería durar (el ajuste del subproceso o el estimado del catálogo, en minutos)
        // y la fecha de entrega comprometida con el cliente.
        java.time.LocalDateTime fechaAsignacion,
        java.time.LocalDateTime fechaCompletado,
        Integer duracionEstimadaMinutos,
        java.time.LocalDate fechaEntregaEstimada,
        // Por qué no se pudo calcular el subservicio (ej. "Falta la criticidad del cargo."); null si
        // se calculó o si el subservicio no tiene cálculo propio.
        String avisoCalculo,
        // Avisos para quien diligencia que NO bloquean (docs/antecedentes, Prompt 6): texto y dónde.
        java.util.List<com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.AvisoFormulario> avisosFormulario,
        // NS-07: la solicitud trae la criticidad del cargo (la eligió el cliente): el campo de criticidad llega precargado y solo lo cambia el gestor.
        boolean criticidadDeSolicitud
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
            List<FilaDatosResponse> filas,
            // idColumna -> justificación de una respuesta crítica (ver ColumnaResponse.opcionesCriticas)
            Map<Integer, String> justificaciones,
            // Tabla que arma el código (FormularioSeccion.calculada): solo lectura, sin agregar filas.
            boolean calculada,
            // V212: quien diligencia puede quitarla (opcional) y si ya la quitó (omitida).
            boolean opcional,
            boolean omitida,
            // V217: grupo con el que se clasifica la sección en el índice (null = sin grupo).
            String grupo) {}

    public record FilaDatosResponse(Integer idFila, String etiqueta, Map<Integer, String> valores,
                                    Map<Integer, String> justificaciones) {}

    /** Una tabla de la hoja de vida del candidato (referencias, educación, experiencia laboral,
     *  inactividades) — se muestra aquí intercalada en el orden del constructor, pero se EDITA
     *  aparte, directo contra el registro real del candidato (ver CandidatoHojaVidaController,
     *  con idCandidato de arriba y clave=origenFijo de esta sección) — no es una copia del
     *  servicio, es la información real y compartida de esa persona.
     *  columnasValidacion/validacionPorId son distintos: son columnas que el administrador agregó
     *  a ESTA sección (FormularioColumna normales) para que analista/poligrafista/visitador
     *  validen cada fila (ej. "¿verificada?", "observación") — SÍ se guardan en este servicio
     *  (FormularioFila/FormularioDato, ver FormularioService), nunca tocan la tabla del
     *  candidato. validacionPorId se indexa por el "id" (String) que trae cada mapa de `filas`. */
    public record TablaFijaResponse(
            Integer idSeccion,
            String nombre,
            Integer orden,
            String clave,
            List<ColumnaFija> columnas,
            List<Map<String, String>> filas,
            List<ColumnaResponse> columnasValidacion,
            Map<String, FilaValidacionResponse> validacionPorId,
            boolean opcional,
            boolean omitida,
            String grupo) {
        public record ColumnaFija(String clave, String etiqueta) {}
        public record FilaValidacionResponse(Integer idFila, Map<Integer, String> valores, Map<Integer, String> justificaciones) {}
    }
}
