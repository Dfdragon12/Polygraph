package com.polygraph.erp.modules.formularios.dto;

/** Qué tan lleno está el formulario de un subproceso.
 *  camposLlenos/camposTotal: solo campos de secciones CAMPOS (el "X/Y" de siempre).
 *  porcentaje: avance por SECCIONES — cada sección pesa igual: CAMPOS aporta la fracción de campos
 *  llenos; TABLA (y tablas de hoja de vida) cuenta completa si tiene al menos un registro. Antes
 *  el avance solo salía de camposTotal, así que una plantilla hecha solo de tablas daba 0/0 y no
 *  se mostraba ningún porcentaje. null si la plantilla no tiene ninguna sección. */
public record ProgresoFormularioResponse(
        Integer camposLlenos,
        Integer camposTotal,
        Integer porcentaje,
        Integer seccionesCompletas,
        Integer seccionesTotal) {}
