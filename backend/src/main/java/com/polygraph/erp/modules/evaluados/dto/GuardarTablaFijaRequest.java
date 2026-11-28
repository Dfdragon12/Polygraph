package com.polygraph.erp.modules.evaluados.dto;

import java.util.List;
import java.util.Map;

/** Reemplazo completo de las filas de UNA tabla de la hoja de vida del candidato (referencias,
 *  educación, experiencia laboral o inactividades) — mismo patrón de "guardar todo de una vez"
 *  que el resto del sistema. id=null -> fila nueva; id existente -> se actualiza esa fila; una
 *  fila que ya existía y no viene en el request se borra. */
public record GuardarTablaFijaRequest(List<FilaFijaRequest> filas) {
    public record FilaFijaRequest(Long id, Map<String, String> valores) {}
}
