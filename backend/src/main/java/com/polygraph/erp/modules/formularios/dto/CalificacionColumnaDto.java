package com.polygraph.erp.modules.formularios.dto;

import java.util.List;

/** Para qué sirve un campo en la calificación por hallazgos (V205) — igual al guardar la plantilla
 *  y al leerla. uso: null | HALLAZGO | CRITICIDAD | CONDICION. calculo: solo columnas calculadas
 *  (PUNTAJE, FORMULA, NIVEL, SEMAFORO_BAJO/MEDIO/ALTO, NUMERO_FILA, NOTA; en la tabla de hallazgos
 *  REF, DESCRIPCION, FUENTE, DETALLE, PUNTOS). referencia/textoNota: NOTA. fuente/detalle: cómo se
 *  escribe el hallazgo ({clave} = valor de ese campo). porOpcion: en una lista normal, qué hallazgo
 *  genera cada opción. Ver FormularioColumna y riesgo/hallazgos/MotorHallazgos. */
public record CalificacionColumnaDto(String uso, String calculo, String referencia, String textoNota,
                                     String fuente, String detalle, List<HallazgoOpcion> porOpcion) {

    public record HallazgoOpcion(String opcion, String codigo) {}

    public static CalificacionColumnaDto de(com.polygraph.erp.modules.formularios.entity.FormularioColumna c) {
        return new CalificacionColumnaDto(c.getUsoCalificacion(), c.getCalculo(), c.getReferencia(), c.getTextoNota(),
                c.getFuenteHallazgo(), c.getDetalleHallazgo(),
                c.getHallazgos().stream().map(h -> new HallazgoOpcion(h.getOpcion(), h.getCodigo())).toList());
    }
}
