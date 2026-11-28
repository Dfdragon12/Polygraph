package com.polygraph.erp.modules.evaluados.dto;

import java.util.List;
import java.util.Map;

/** Una tabla editable de la hoja de vida REAL del candidato (referencias, educación, experiencia
 *  laboral, inactividades) — a diferencia de FormularioResponse.TablaFijaResponse (que es de solo
 *  lectura, intercalada en el formulario de un servicio puntual), esto es lo que devuelve/recibe
 *  CandidatoHojaVidaController: cada fila trae su id real, para poder editarla o borrarla. */
public record TablaFijaEditableResponse(
        String clave,
        String etiqueta,
        List<ColumnaFija> columnas,
        List<FilaFija> filas
) {
    public record ColumnaFija(String clave, String etiqueta, String tipoDato) {}

    public record FilaFija(Long id, Map<String, String> valores) {}
}
